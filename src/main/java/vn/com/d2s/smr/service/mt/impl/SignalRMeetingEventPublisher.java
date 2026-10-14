package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vn.com.d2s.smr.config.HubAuthHandshakeInterceptor;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MtAttendanceEvent;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MtWhiteboardRepository;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;
import vn.com.d2s.smr.service.mt.MeetingEventPublisher;
import vn.com.d2s.smr.service.nt.UserNotificationPublisher;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Gateway tương thích giao thức SignalR (JSON) cho /meetinghub.
 *
 * <p>Hai loại kênh:</p>
 * <ul>
 *   <li>Theo phòng họp ({@code JoinMeeting}) — chỉ thành viên cuộc họp mới vào được.</li>
 *   <li>Theo người dùng — mọi kết nối đã xác thực của một user (nhiều tab/thiết bị),
 *       dùng cho thông báo cá nhân ({@link UserNotificationPublisher}).</li>
 * </ul>
 * Danh tính lấy từ {@link HubAuthHandshakeInterceptor} (JWT lúc bắt tay), không tin dữ liệu client gửi.
 */
@Component
@Primary
public class SignalRMeetingEventPublisher extends TextWebSocketHandler
        implements MeetingEventPublisher, UserNotificationPublisher, RoomEventPublisher {

    private static final String RECORD_SEPARATOR = "\u001e";
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    // Map: meetingId -> Set of WebSocketSessions
    private final Map<String, Set<WebSocketSession>> meetingRooms = new ConcurrentHashMap<>();
    // Map: sessionId -> meetingId
    private final Map<String, String> sessionMeetingMap = new ConcurrentHashMap<>();
    // Map: userName (lower-case) -> mọi kết nối của user đó
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    // Mọi kết nối đang mở — để gửi ping keep-alive
    private final Set<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();
    private static final String PING_FRAME = "{\"type\":6}" + RECORD_SEPARATOR;
    // Map: sessionId -> ClientSessionId (FE sinh mỗi lần vào phòng) để ghép cặp vào/rời khi tính thời lượng
    private final Map<String, String> sessionClientIds = new ConcurrentHashMap<>();
    // Map: meetingId -> bảng trắng đang khóa? (nạp lười từ DB, cập nhật qua setWhiteboardLocked)
    private final Map<String, Boolean> lockedBoards = new ConcurrentHashMap<>();
    private final vn.com.d2s.smr.service.mt.MeetingTranscriptService transcriptService;
    private final MeetingPersonalRepository personalRepository;
    private final MeetingInfoRepository meetingRepository;
    private final MtWhiteboardRepository whiteboardRepository;
    private final RoomAdmission admission;
    private final MeetingAttendanceService attendanceService;

    public SignalRMeetingEventPublisher(
            vn.com.d2s.smr.service.mt.MeetingTranscriptService transcriptService,
            MeetingPersonalRepository personalRepository,
            MeetingInfoRepository meetingRepository,
            MtWhiteboardRepository whiteboardRepository,
            RoomAdmission admission,
            MeetingAttendanceService attendanceService
    ) {
        this.transcriptService = transcriptService;
        this.personalRepository = personalRepository;
        this.meetingRepository = meetingRepository;
        this.whiteboardRepository = whiteboardRepository;
        this.admission = admission;
        this.attendanceService = attendanceService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        allSessions.add(session);
        String userName = userNameOf(session);
        if (userName != null) {
            userSessions.computeIfAbsent(key(userName), k -> new CopyOnWriteArraySet<>()).add(session);
        }
        // Chờ handshake message của SignalR
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        String[] frames = payload.split(RECORD_SEPARATOR);

        for (String frame : frames) {
            String trimmed = frame.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            JsonNode node = objectMapper.readTree(trimmed);

            // 1. Handshake request: {"protocol": "json", "version": 1}
            if (node.has("protocol")) {
                send(session, "{}" + RECORD_SEPARATOR);
                continue;
            }

            // 2. Invocation request: {"type": 1, "target": "...", "arguments": [...], "invocationId": "..."}
            if (node.has("type") && node.get("type").asInt() == 1) {
                String target = node.has("target") ? node.get("target").asText() : "";
                JsonNode args = node.get("arguments");
                String invocationId = node.has("invocationId") ? node.get("invocationId").asText() : null;

                handleInvocation(session, target, args, invocationId);
            }
        }
    }

    private void handleInvocation(WebSocketSession session, String target, JsonNode args, String invocationId) throws IOException {
        String error = null;
        String meetingId = args != null && args.size() > 0 ? args.get(0).asText() : null;
        if ("JoinMeeting".equalsIgnoreCase(target) && meetingId != null) {
            // Tham số 2 (tuỳ chọn): ClientSessionId — mỗi lần vào phòng FE sinh một mã để ghép cặp vào/rời.
            String clientSessionId = args.size() > 1 && !args.get(1).isNull() ? args.get(1).asText() : session.getId();
            error = canJoin(session, meetingId);
            if (error == null) {
                String current = sessionMeetingMap.get(session.getId());
                if (current != null && !current.equals(meetingId)) {
                    leaveRoom(session, current);
                }
                if (!meetingId.equals(current)) {
                    sessionClientIds.put(session.getId(), clientSessionId);
                    joinRoom(session, meetingId);
                }
            }
        } else if ("LeaveMeeting".equalsIgnoreCase(target) && meetingId != null) {
            if (inRoom(session, meetingId)) {
                leaveRoom(session, meetingId);
            }
        } else if ("SendMessage".equalsIgnoreCase(target)) {
            // Chat đi qua REST POST /api/Meeting/{id}/messages để lưu DB và lấy danh tính người gửi từ server.
            error = "Gửi tin nhắn qua API /api/Meeting/{id}/messages.";
        } else if ("SendWhiteboardOp".equalsIgnoreCase(target) && meetingId != null && args.size() > 1) {
            if (!inRoom(session, meetingId)) {
                error = "Bạn chưa tham gia phòng họp này.";
            } else if (!canDraw(session, meetingId)) {
                error = "Chủ trì đã khóa bảng trắng.";
            } else {
                // Nét vẽ tạm thời (chưa lưu) — gửi cho người khác trong phòng; người vẽ tự lưu bản đầy đủ qua REST.
                broadcastToRoom(meetingId, "WhiteboardOp", Map.of(
                        "meetingId", meetingId,
                        "by", userNameOf(session) == null ? "" : userNameOf(session),
                        "op", args.get(1)), session);
            }
        } else if ("SendCaption".equalsIgnoreCase(target) && meetingId != null && args.size() > 1) {
            if (inRoom(session, meetingId)) {
                JsonNode captionEnvelope = args.get(1);
                try {
                    vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto dto = objectMapper.treeToValue(captionEnvelope, vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto.class);
                    // Người nói lấy theo kết nối, không tin dữ liệu client gửi.
                    String userName = userNameOf(session);
                    if (userName != null) {
                        dto.setSpeakerUserName(userName);
                        dto.setSpeakerId(userName);
                        dto.setSpeakerName(fullNameOf(session, meetingId));
                    }
                    dto.setMeetingId(meetingId);
                    if (dto.isFinal()) {
                        transcriptService.saveTranscriptSegment(meetingId, dto);
                    }
                    broadcastToRoom(meetingId, "ReceiveCaption", dto);
                } catch (Exception ignored) {
                    // phụ đề sai định dạng → bỏ qua
                }
            } else {
                error = "Bạn chưa tham gia phòng họp này.";
            }
        }

        if (invocationId != null) {
            // Completion message: SignalR client reject promise khi có "error".
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("type", 3);
            response.put("invocationId", invocationId);
            if (error != null) {
                response.put("error", error);
            } else {
                response.put("result", null);
            }
            send(session, objectMapper.writeValueAsString(response) + RECORD_SEPARATOR);
        }
    }

    /**
     * Trả lỗi (null = được vào). Phải là thành viên và đã qua phòng chờ (RoomAdmission = ADMITTED),
     * nên không thể bỏ qua phòng chờ bằng cách gọi thẳng JoinMeeting.
     */
    private String canJoin(WebSocketSession session, String meetingId) {
        String userName = userNameOf(session);
        if (userName == null) {
            // Chỉ xảy ra khi smr.hub.require-auth=false (chế độ tương thích).
            return null;
        }
        Optional<MeetingPersonal> personal = personalRepository.findByMeetingIdAndUserName(meetingId, userName);
        if (personal.isEmpty()) {
            return "Bạn không phải thành viên cuộc họp này.";
        }
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(meetingId).orElse(null);
        if (meeting == null) {
            return "Cuộc họp không tồn tại.";
        }
        RoomAdmission.State state = admission.evaluate(meeting, personal.get());
        return state == RoomAdmission.State.ADMITTED ? null : "Chưa được vào phòng (" + state.name() + ").";
    }

    private boolean canDraw(WebSocketSession session, String meetingId) {
        boolean locked = lockedBoards.computeIfAbsent(meetingId, id ->
                whiteboardRepository.findFirstByMeetingId(id).map(b -> b.isLocked()).orElse(false));
        if (!locked) {
            return true;
        }
        String userName = userNameOf(session);
        return userName != null && personalRepository.findByMeetingIdAndUserName(meetingId, userName)
                .map(RoomAdmission::isModerator).orElse(false);
    }

    private String fullNameOf(WebSocketSession session, String meetingId) {
        Object cached = session.getAttributes().get("smr.fullName." + meetingId);
        if (cached instanceof String name) {
            return name;
        }
        String userName = userNameOf(session);
        String name = personalRepository.findByMeetingIdAndUserName(meetingId, userName)
                .map(MeetingPersonal::getFullName).orElse(userName);
        session.getAttributes().put("smr.fullName." + meetingId, name);
        return name;
    }

    private boolean inRoom(WebSocketSession session, String meetingId) {
        return meetingId != null && meetingId.equals(sessionMeetingMap.get(session.getId()));
    }

    private synchronized void joinRoom(WebSocketSession session, String meetingId) {
        meetingRooms.computeIfAbsent(meetingId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionMeetingMap.put(session.getId(), meetingId);
        String userName = userNameOf(session);
        recordAttendance(meetingId, userName, MtAttendanceEvent.ENTER, session);

        broadcastToRoom(meetingId, "PresenceChanged", Map.of(
                "meetingId", meetingId,
                "userName", userName == null ? "" : userName,
                "action", "joined",
                "connectionId", session.getId()
        ));
    }

    private void recordAttendance(String meetingId, String userName, int type, WebSocketSession session) {
        if (userName == null) {
            return;
        }
        try {
            attendanceService.record(meetingId, userName, type, sessionClientIds.getOrDefault(session.getId(), session.getId()));
        } catch (RuntimeException ignored) {
            // DB lỗi không được làm hỏng kết nối realtime
        }
    }

    private synchronized void leaveRoom(WebSocketSession session, String meetingId) {
        Set<WebSocketSession> room = meetingRooms.get(meetingId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                meetingRooms.remove(meetingId);
            }
        }
        if (sessionMeetingMap.remove(session.getId()) == null) {
            return;
        }
        String userName = userNameOf(session);
        recordAttendance(meetingId, userName, MtAttendanceEvent.LEAVE, session);
        sessionClientIds.remove(session.getId());

        broadcastToRoom(meetingId, "PresenceChanged", Map.of(
                "meetingId", meetingId,
                "userName", userName == null ? "" : userName,
                "action", "left",
                "connectionId", session.getId()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        allSessions.remove(session);
        String meetingId = sessionMeetingMap.get(session.getId());
        if (meetingId != null) {
            leaveRoom(session, meetingId);
        }
        String userName = userNameOf(session);
        if (userName != null) {
            userSessions.computeIfPresent(key(userName), (k, set) -> {
                set.remove(session);
                return set.isEmpty() ? null : set;
            });
        }
    }

    /**
     * Keep-alive theo giao thức SignalR: client mặc định ngắt nếu 30s không nhận gì từ server
     * (serverTimeoutInMilliseconds), nên server gửi Ping (type 6) mỗi 15s.
     */
    @Scheduled(fixedRateString = "${smr.hub.keep-alive-ms:15000}")
    public void sendKeepAlive() {
        for (WebSocketSession session : allSessions) {
            if (session.isOpen()) {
                send(session, PING_FRAME);
            } else {
                allSessions.remove(session);
            }
        }
    }

    @Override
    public void publishFilesChanged(String meetingId, String fileId, String action) {
        broadcastToRoom(meetingId, "FilesChanged", Map.of(
                "meetingId", meetingId,
                "fileId", fileId,
                "action", action
        ));
    }

    @Override
    public void publishTaskChanged(String meetingId, String taskId, String action) {
        broadcastToRoom(meetingId, "TaskChanged", Map.of(
                "meetingId", meetingId,
                "taskId", taskId,
                "action", action
        ));
    }

    @Override
    public void publishMeetingStatusChanged(String meetingId, String status) {
        broadcastToRoom(meetingId, "MeetingStatusChanged", Map.of(
                "meetingId", meetingId,
                "status", status
        ));
    }

    @Override
    public void publishCaption(String meetingId, Object captionPayload) {
        broadcastToRoom(meetingId, "ReceiveCaption", captionPayload);
    }

    @Override
    public void publishToUsers(Collection<String> userNames, String target, Object payload) {
        if (userNames == null || userNames.isEmpty()) {
            return;
        }
        String json = frame(target, payload);
        if (json == null) {
            return;
        }
        for (String userName : userNames) {
            Set<WebSocketSession> sessions = userSessions.get(key(userName));
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                send(session, json);
            }
        }
    }

    @Override
    public void broadcastToRoom(String meetingId, String target, Object payload) {
        broadcastToRoom(meetingId, target, payload, null);
    }

    private void broadcastToRoom(String meetingId, String target, Object payload, WebSocketSession except) {
        Set<WebSocketSession> room = meetingRooms.get(meetingId);
        if (room == null || room.isEmpty()) {
            return;
        }
        String json = frame(target, payload);
        if (json == null) {
            return;
        }
        for (WebSocketSession session : room) {
            if (session != except) {
                send(session, json);
            }
        }
    }

    @Override
    public void sendToUsers(Collection<String> userNames, String target, Object payload) {
        publishToUsers(userNames, target, payload);
    }

    @Override
    public Set<String> onlineUsers(String meetingId) {
        Set<WebSocketSession> room = meetingRooms.get(meetingId);
        Set<String> users = new HashSet<>();
        if (room != null) {
            for (WebSocketSession session : room) {
                String userName = userNameOf(session);
                if (userName != null && session.isOpen()) {
                    users.add(key(userName));
                }
            }
        }
        return users;
    }

    @Override
    public void removeUserFromRoom(String meetingId, String userName) {
        Set<WebSocketSession> room = meetingRooms.get(meetingId);
        if (room == null || userName == null) {
            return;
        }
        for (WebSocketSession session : room) {
            if (key(userName).equals(userNameOf(session) == null ? null : key(userNameOf(session)))) {
                leaveRoom(session, meetingId);
            }
        }
    }

    @Override
    public void setWhiteboardLocked(String meetingId, boolean locked) {
        lockedBoards.put(meetingId, locked);
    }

    private String frame(String target, Object payload) {
        try {
            Map<String, Object> messageMap = Map.of(
                    "type", 1,
                    "target", target,
                    "arguments", new Object[]{payload}
            );
            return objectMapper.writeValueAsString(messageMap) + RECORD_SEPARATOR;
        } catch (Exception e) {
            return null; // Ignore format exception
        }
    }

    /** WebSocketSession không thread-safe khi gửi đồng thời (event nghiệp vụ + phòng họp). */
    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException | IllegalStateException e) {
                // Ignore individual session send failure
            }
        }
    }

    private static String userNameOf(WebSocketSession session) {
        Object value = session.getAttributes().get(HubAuthHandshakeInterceptor.ATTR_USER_NAME);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static String key(String userName) {
        return userName.toLowerCase(Locale.ROOT);
    }
}
