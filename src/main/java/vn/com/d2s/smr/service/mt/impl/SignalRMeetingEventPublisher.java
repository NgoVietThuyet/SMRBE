package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import vn.com.d2s.smr.service.mt.MeetingEventPublisher;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Primary
public class SignalRMeetingEventPublisher extends TextWebSocketHandler implements MeetingEventPublisher {

    private static final String RECORD_SEPARATOR = "\u001e";
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map: meetingId -> Set of WebSocketSessions
    private final Map<String, Set<WebSocketSession>> meetingRooms = new ConcurrentHashMap<>();
    // Map: sessionId -> meetingId
    private final Map<String, String> sessionMeetingMap = new ConcurrentHashMap<>();
    private final vn.com.d2s.smr.service.mt.MeetingTranscriptService transcriptService;

    public SignalRMeetingEventPublisher(vn.com.d2s.smr.service.mt.MeetingTranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Connection established, wait for SignalR handshake message
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
                session.sendMessage(new TextMessage("{}" + RECORD_SEPARATOR));
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
        if ("JoinMeeting".equalsIgnoreCase(target) && args != null && args.size() > 0) {
            String meetingId = args.get(0).asText();
            joinRoom(session, meetingId);
        } else if ("LeaveMeeting".equalsIgnoreCase(target) && args != null && args.size() > 0) {
            String meetingId = args.get(0).asText();
            leaveRoom(session, meetingId);
        } else if ("SendMessage".equalsIgnoreCase(target) && args != null && args.size() > 1) {
            String meetingId = args.get(0).asText();
            JsonNode messageEnvelope = args.get(1);
            broadcastToRoom(meetingId, "ReceiveMessage", messageEnvelope);
        } else if ("SendCaption".equalsIgnoreCase(target) && args != null && args.size() > 1) {
            String meetingId = args.get(0).asText();
            JsonNode captionEnvelope = args.get(1);
            try {
                vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto dto = objectMapper.treeToValue(captionEnvelope, vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto.class);
                transcriptService.saveTranscriptSegment(meetingId, dto);
            } catch (Exception ignored) {
            }
            broadcastToRoom(meetingId, "ReceiveCaption", captionEnvelope);
        }

        if (invocationId != null) {
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("type", 3);
            response.put("invocationId", invocationId);
            response.put("result", null);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response) + RECORD_SEPARATOR));
        }
    }

    private synchronized void joinRoom(WebSocketSession session, String meetingId) {
        meetingRooms.computeIfAbsent(meetingId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionMeetingMap.put(session.getId(), meetingId);

        broadcastToRoom(meetingId, "PresenceChanged", Map.of(
                "meetingId", meetingId,
                "action", "hub_joined",
                "connectionId", session.getId()
        ));
        broadcastToRoom(meetingId, "ParticipantJoined", Map.of());
    }

    private synchronized void leaveRoom(WebSocketSession session, String meetingId) {
        Set<WebSocketSession> room = meetingRooms.get(meetingId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                meetingRooms.remove(meetingId);
            }
        }
        sessionMeetingMap.remove(session.getId());

        broadcastToRoom(meetingId, "PresenceChanged", Map.of(
                "meetingId", meetingId,
                "action", "hub_left",
                "connectionId", session.getId()
        ));
        broadcastToRoom(meetingId, "ParticipantLeft", Map.of());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String meetingId = sessionMeetingMap.get(session.getId());
        if (meetingId != null) {
            leaveRoom(session, meetingId);
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


    private void broadcastToRoom(String meetingId, String target, Object payload) {
        Set<WebSocketSession> room = meetingRooms.get(meetingId);
        if (room == null || room.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> messageMap = Map.of(
                    "type", 1,
                    "target", target,
                    "arguments", new Object[]{payload}
            );
            String jsonMessage = objectMapper.writeValueAsString(messageMap) + RECORD_SEPARATOR;
            TextMessage textMessage = new TextMessage(jsonMessage);

            for (WebSocketSession session : room) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        // Ignore individual session send failure
                    }
                }
            }
        } catch (Exception e) {
            // Ignore format exception
        }
    }
}
