package vn.com.d2s.smr.service.mt.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.config.AdminAccess;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSettingsDto;
import vn.com.d2s.smr.dto.mt.room.AttendanceSummaryResponse;
import vn.com.d2s.smr.dto.mt.room.EnterRoomResponse;
import vn.com.d2s.smr.dto.mt.room.LobbyRequestResponse;
import vn.com.d2s.smr.dto.mt.room.PublicMeetingResponse;
import vn.com.d2s.smr.dto.mt.room.RoomParticipantResponse;
import vn.com.d2s.smr.dto.mt.room.SaveWhiteboardRequest;
import vn.com.d2s.smr.dto.mt.room.WhiteboardResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MtLobbyRequest;
import vn.com.d2s.smr.entity.mt.MtWhiteboard;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MtLobbyRequestRepository;
import vn.com.d2s.smr.repository.mt.MtWhiteboardRepository;
import vn.com.d2s.smr.service.ad.PermissionService;
import vn.com.d2s.smr.service.mt.MeetingRoomService;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MeetingRoomServiceImpl implements MeetingRoomService {

    /** Giới hạn nội dung bảng trắng (JSON) để tránh ghi bản ghi quá lớn. */
    static final int WHITEBOARD_MAX_CHARS = 2_000_000;

    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final MtLobbyRequestRepository lobbyRepository;
    private final MtWhiteboardRepository whiteboardRepository;
    private final AdAccountRepository accountRepository;
    private final RoomAdmission admission;
    private final MeetingAttendanceService attendanceService;
    private final AdminAccess adminAccess;
    private final PermissionService permissionService;
    private final Clock clock;
    private RoomEventPublisher publisher = RoomEventPublisher.NO_OP;

    @Autowired
    public MeetingRoomServiceImpl(
            MeetingInfoRepository meetingRepository,
            MeetingPersonalRepository personalRepository,
            MtLobbyRequestRepository lobbyRepository,
            MtWhiteboardRepository whiteboardRepository,
            AdAccountRepository accountRepository,
            RoomAdmission admission,
            MeetingAttendanceService attendanceService,
            AdminAccess adminAccess,
            PermissionService permissionService
    ) {
        this(meetingRepository, personalRepository, lobbyRepository, whiteboardRepository, accountRepository,
                admission, attendanceService, adminAccess, permissionService, Clock.systemUTC());
    }

    MeetingRoomServiceImpl(
            MeetingInfoRepository meetingRepository,
            MeetingPersonalRepository personalRepository,
            MtLobbyRequestRepository lobbyRepository,
            MtWhiteboardRepository whiteboardRepository,
            AdAccountRepository accountRepository,
            RoomAdmission admission,
            MeetingAttendanceService attendanceService,
            AdminAccess adminAccess,
            PermissionService permissionService,
            Clock clock
    ) {
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.lobbyRepository = lobbyRepository;
        this.whiteboardRepository = whiteboardRepository;
        this.accountRepository = accountRepository;
        this.admission = admission;
        this.attendanceService = attendanceService;
        this.adminAccess = adminAccess;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setPublisher(RoomEventPublisher publisher) {
        this.publisher = publisher;
    }

    // ===================== Vào phòng / phòng chờ =====================

    @Override
    @Transactional
    public EnterRoomResponse enter(String userName, String meetingId) {
        MeetingInfo meeting = meeting(meetingId);
        MeetingPersonal me = personalRepository.findByMeetingIdAndUserName(meetingId, userName).orElse(null);
        if (me == null) {
            me = selfJoin(meeting, userName);
        }
        RoomAdmission.State state = admission.evaluate(meeting, me);
        String requestId = null;
        if (state == RoomAdmission.State.NEED_REQUEST) {
            requestId = createRequest(meeting, me).getId();
            state = RoomAdmission.State.LOBBY;
        } else if (state == RoomAdmission.State.LOBBY) {
            requestId = latestRequest(meetingId, me.getUserName()).map(MtLobbyRequest::getId).orElse(null);
        }
        return new EnterRoomResponse(state.name(), requestId, message(state), RoomAdmission.isModerator(me));
    }

    /**
     * Người chưa được mời: cuộc họp Nội bộ (1) / Công khai (2) đang hoặc sắp diễn ra → thêm làm thành viên "Tùy chọn".
     * Cuộc họp "Chỉ người được mời" → 403.
     */
    private MeetingPersonal selfJoin(MeetingInfo meeting, String userName) {
        boolean open = meeting.getVisibility() >= 1 && (meeting.getStatus() == 1 || meeting.getStatus() == 2);
        if (!open || RoomAdmission.isGuest(userName)) {
            throw new SecurityException("Cuộc họp chỉ dành cho người được mời.");
        }
        AdAccount account = accountRepository.findById(userName)
                .orElseThrow(() -> new SecurityException("Cuộc họp chỉ dành cho người được mời."));
        LocalDateTime now = LocalDateTime.now(clock);
        MeetingPersonal p = new MeetingPersonal();
        p.setId(UUID.randomUUID().toString().replace("-", ""));
        p.setMeetingId(meeting.getId());
        p.setUserName(account.getUserName());
        p.setFullName(account.getFullName());
        p.setPhone(nz(account.getPhone()));
        p.setEmail(nz(account.getEmail()));
        p.setAddress(nz(account.getAddress()));
        p.setOrgId(nz(account.getOrgId()));
        p.setTitleCode(nz(account.getTitleCode()));
        p.setReferenceFileId(nz(meeting.getReferenceFileId()));
        p.setType(5);
        p.setChairperson(false);
        p.setJoined(false);
        p.setCreateBy(userName);
        p.setCreateDate(now);
        p.setUpdateBy(userName);
        p.setUpdateDate(now);
        return personalRepository.save(p);
    }

    @Override
    @Transactional
    public EnterRoomResponse requestAgain(String userName, String meetingId) {
        MeetingInfo meeting = meeting(meetingId);
        MeetingPersonal me = member(meetingId, userName);
        RoomAdmission.State state = admission.evaluate(meeting, me);
        String requestId = null;
        if (state == RoomAdmission.State.REJECTED || state == RoomAdmission.State.NEED_REQUEST) {
            requestId = createRequest(meeting, me).getId();
            state = RoomAdmission.State.LOBBY;
        }
        return new EnterRoomResponse(state.name(), requestId, message(state), RoomAdmission.isModerator(me));
    }

    private MtLobbyRequest createRequest(MeetingInfo meeting, MeetingPersonal me) {
        MtLobbyRequest r = new MtLobbyRequest();
        r.setId(UUID.randomUUID().toString().replace("-", ""));
        r.setMeetingId(meeting.getId());
        r.setUserName(me.getUserName());
        r.setDisplayName(truncate(me.getFullName(), 200));
        r.setStatus(MtLobbyRequest.PENDING);
        r.setRequestedAt(LocalDateTime.now(clock));
        lobbyRepository.save(r);
        notifyLobbyChanged(meeting.getId());
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LobbyRequestResponse> lobby(String userName, String meetingId) {
        moderator(meetingId, userName);
        return lobbyRepository.findByMeetingIdAndStatusOrderByRequestedAtAsc(meetingId, MtLobbyRequest.PENDING).stream()
                .map(r -> new LobbyRequestResponse(r.getId(), r.getUserName(), r.getDisplayName(),
                        RoomAdmission.isGuest(r.getUserName()), r.getRequestedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void decide(String userName, String meetingId, String requestId, boolean admit) {
        moderator(meetingId, userName);
        MtLobbyRequest r = lobbyRepository.findById(requestId)
                .filter(x -> x.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu vào phòng không tồn tại."));
        if (r.getStatus() != MtLobbyRequest.PENDING) {
            return; // chủ trì khác đã xử lý
        }
        applyDecision(r, userName, admit);
        notifyLobbyChanged(meetingId);
    }

    @Override
    @Transactional
    public int admitAll(String userName, String meetingId) {
        moderator(meetingId, userName);
        List<MtLobbyRequest> pending = lobbyRepository.findByMeetingIdAndStatusOrderByRequestedAtAsc(meetingId, MtLobbyRequest.PENDING);
        pending.forEach(r -> applyDecision(r, userName, true));
        if (!pending.isEmpty()) {
            notifyLobbyChanged(meetingId);
        }
        return pending.size();
    }

    private void applyDecision(MtLobbyRequest r, String decidedBy, boolean admit) {
        r.setStatus(admit ? MtLobbyRequest.ADMITTED : MtLobbyRequest.REJECTED);
        r.setDecidedBy(decidedBy);
        r.setDecidedAt(LocalDateTime.now(clock));
        lobbyRepository.save(r);
        publisher.sendToUsers(List.of(r.getUserName()), "LobbyDecision", Map.of(
                "meetingId", r.getMeetingId(),
                "state", admit ? "ADMITTED" : "REJECTED"));
    }

    private void notifyLobbyChanged(String meetingId) {
        List<String> moderators = personalRepository.findByMeetingId(meetingId).stream()
                .filter(RoomAdmission::isModerator)
                .map(MeetingPersonal::getUserName)
                .toList();
        int pending = lobbyRepository.findByMeetingIdAndStatusOrderByRequestedAtAsc(meetingId, MtLobbyRequest.PENDING).size();
        publisher.sendToUsers(moderators, "LobbyChanged", Map.of("meetingId", meetingId, "pending", pending));
    }

    // ===================== Thành viên trong phòng =====================

    @Override
    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> participants(String userName, String meetingId) {
        member(meetingId, userName);
        Set<String> online = publisher.onlineUsers(meetingId);
        return personalRepository.findByMeetingId(meetingId).stream()
                .map(p -> new RoomParticipantResponse(p.getUserName(), p.getFullName(), p.getType(),
                        RoomAdmission.isModerator(p), RoomAdmission.isGuest(p.getUserName()),
                        online.contains(p.getUserName().toLowerCase(Locale.ROOT))))
                .sorted(Comparator.comparing(RoomParticipantResponse::online).reversed()
                        .thenComparingInt(RoomParticipantResponse::role)
                        .thenComparing(RoomParticipantResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /** Chủ trì chỉ tắt được mic người khác (không bật hộ) — client nhận ForceMute và tự tắt mic. */
    @Override
    public void mute(String userName, String meetingId, String target) {
        moderator(meetingId, userName);
        MeetingPersonal t = member(meetingId, target);
        if (t.getUserName().equalsIgnoreCase(userName)) {
            throw new IllegalArgumentException("Không thể tự tắt mic qua chức năng này.");
        }
        publisher.sendToUsers(List.of(t.getUserName()), "ForceMute", Map.of("meetingId", meetingId, "by", userName));
    }

    @Override
    @Transactional
    public void kick(String userName, String meetingId, String target) {
        moderator(meetingId, userName);
        MeetingPersonal t = member(meetingId, target);
        if (t.getUserName().equalsIgnoreCase(userName)) {
            throw new IllegalArgumentException("Không thể tự mời mình ra khỏi phòng.");
        }
        if (RoomAdmission.isOwner(t)) {
            throw new SecurityException("Không thể mời chủ trì ra khỏi phòng.");
        }
        // Lượt "bị mời ra" lưu như yêu cầu bị từ chối → muốn vào lại phải xin chủ trì duyệt.
        MtLobbyRequest r = new MtLobbyRequest();
        r.setId(UUID.randomUUID().toString().replace("-", ""));
        r.setMeetingId(meetingId);
        r.setUserName(t.getUserName());
        r.setDisplayName(truncate(t.getFullName(), 200));
        r.setStatus(MtLobbyRequest.REJECTED);
        r.setRequestedAt(LocalDateTime.now(clock));
        r.setDecidedBy(userName);
        r.setDecidedAt(r.getRequestedAt());
        lobbyRepository.save(r);

        t.setJoined(false);
        personalRepository.save(t);
        publisher.sendToUsers(List.of(t.getUserName()), "Kicked", Map.of("meetingId", meetingId, "by", userName));
        publisher.removeUserFromRoom(meetingId, t.getUserName());
        publisher.broadcastToRoom(meetingId, "ParticipantsChanged", Map.of("meetingId", meetingId));
    }

    @Override
    @Transactional
    public void changeRole(String userName, String meetingId, String target, int role) {
        moderator(meetingId, userName);
        if (role < 1 || role > 5) {
            throw new IllegalArgumentException("Vai trò không hợp lệ.");
        }
        MeetingPersonal t = member(meetingId, target);
        if (RoomAdmission.isGuest(t.getUserName()) && role <= 3) {
            throw new IllegalArgumentException("Khách không thể làm chủ trì, đồng chủ trì hoặc thư ký.");
        }
        if (RoomAdmission.isOwner(t)) {
            throw new IllegalStateException("Không đổi được vai trò của chủ trì (người tạo cuộc họp).");
        }
        if (role == 1) {
            throw new IllegalArgumentException("Chủ trì là người tạo cuộc họp. Hãy chọn Đồng chủ trì để trao quyền quản lý.");
        }
        t.setType(role);
        t.setChairperson(role == 2);
        t.setUpdateBy(userName);
        t.setUpdateDate(LocalDateTime.now(clock));
        personalRepository.save(t);
        publisher.sendToUsers(List.of(t.getUserName()), "RoleChanged", Map.of("meetingId", meetingId, "role", role));
        publisher.broadcastToRoom(meetingId, "ParticipantsChanged", Map.of("meetingId", meetingId));
    }

    // ===================== Bảng trắng =====================

    @Override
    @Transactional(readOnly = true)
    public WhiteboardResponse whiteboard(String userName, String meetingId) {
        member(meetingId, userName);
        return whiteboardRepository.findFirstByMeetingId(meetingId)
                .map(MeetingRoomServiceImpl::toResponse)
                .orElse(new WhiteboardResponse(0, "[]", false, null, null));
    }

    @Override
    @Transactional
    public WhiteboardResponse saveWhiteboard(String userName, String meetingId, SaveWhiteboardRequest request) {
        MeetingInfo meeting = meeting(meetingId);
        MeetingPersonal me = member(meetingId, userName);
        if (meeting.getStatus() != 2) {
            throw new IllegalStateException("Chỉ vẽ được khi cuộc họp đang diễn ra.");
        }
        MeetingSettingsDto settings = admission.settings(meeting);
        boolean moderator = RoomAdmission.isModerator(me);
        if (!settings.whiteboardEnabled() && !moderator) {
            throw new SecurityException("Bảng trắng đang tắt trong cuộc họp này.");
        }
        String content = request == null || request.contentJson() == null ? "[]" : request.contentJson();
        if (content.length() > WHITEBOARD_MAX_CHARS) {
            throw new IllegalArgumentException("Bảng trắng quá lớn. Hãy xóa bớt nét vẽ.");
        }
        MtWhiteboard board = whiteboardRepository.findFirstByMeetingId(meetingId).orElseGet(() -> {
            MtWhiteboard b = new MtWhiteboard();
            b.setId(UUID.randomUUID().toString().replace("-", ""));
            b.setMeetingId(meetingId);
            return b;
        });
        if (board.isLocked() && !moderator) {
            throw new SecurityException("Chủ trì đã khóa bảng trắng.");
        }
        if (request.baseRevision() != board.getRevision()) {
            throw new WhiteboardConflictException(toResponse(board));
        }
        board.setRevision(board.getRevision() + 1);
        board.setContentJson(content);
        board.setUpdatedBy(userName);
        board.setUpdatedAt(LocalDateTime.now(clock));
        whiteboardRepository.save(board);
        publisher.broadcastToRoom(meetingId, "WhiteboardSaved", Map.of(
                "meetingId", meetingId, "revision", board.getRevision(), "by", userName));
        return toResponse(board);
    }

    @Override
    @Transactional
    public WhiteboardResponse lockWhiteboard(String userName, String meetingId, boolean locked) {
        moderator(meetingId, userName);
        MtWhiteboard board = whiteboardRepository.findFirstByMeetingId(meetingId).orElseGet(() -> {
            MtWhiteboard b = new MtWhiteboard();
            b.setId(UUID.randomUUID().toString().replace("-", ""));
            b.setMeetingId(meetingId);
            return b;
        });
        board.setLocked(locked);
        board.setUpdatedBy(userName);
        board.setUpdatedAt(LocalDateTime.now(clock));
        whiteboardRepository.save(board);
        publisher.setWhiteboardLocked(meetingId, locked);
        publisher.broadcastToRoom(meetingId, "WhiteboardLockChanged", Map.of("meetingId", meetingId, "locked", locked));
        return toResponse(board);
    }

    private static WhiteboardResponse toResponse(MtWhiteboard b) {
        return new WhiteboardResponse(b.getRevision(), b.getContentJson(), b.isLocked(), b.getUpdatedBy(), b.getUpdatedAt());
    }

    // ===================== Ghi hình =====================

    /** Chỉ ghi nhận + báo cả phòng (Jitsi tự ghi qua startRecording). Audit ở controller. */
    @Override
    public void recording(String userName, String meetingId, boolean on) {
        MeetingInfo meeting = meeting(meetingId);
        moderator(meetingId, userName);
        if (!admission.settings(meeting).recordingEnabled()) {
            throw new SecurityException("Cuộc họp này không cho phép ghi hình.");
        }
        if (!adminAccess.isAdmin(userName) && !hasRight(userName, "MEETING_RECORD")) {
            throw new SecurityException("Bạn chưa được cấp quyền ghi hình (MEETING_RECORD).");
        }
        publisher.broadcastToRoom(meetingId, "RecordingChanged", Map.of("meetingId", meetingId, "on", on, "by", userName));
    }

    private boolean hasRight(String userName, String code) {
        try {
            return permissionService.getEffectivePermissions(userName).stream()
                    .anyMatch(p -> code.equalsIgnoreCase(p.code()) && p.allowed());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ===================== Tham dự =====================

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceSummaryResponse> attendance(String userName, String meetingId) {
        MeetingInfo meeting = meeting(meetingId);
        List<MeetingPersonal> personals = personalRepository.findByMeetingId(meetingId);
        checkAttendanceAccess(userName, personals);
        return attendanceService.summarize(meeting, personals);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedFile exportAttendance(String userName, String meetingId) {
        MeetingInfo meeting = meeting(meetingId);
        List<MeetingPersonal> personals = personalRepository.findByMeetingId(meetingId);
        checkAttendanceAccess(userName, personals);
        byte[] content = attendanceService.exportXlsx(meeting, attendanceService.summarize(meeting, personals));
        LocalDateTime start = meeting.getStartDate() != null ? meeting.getStartDate() : meeting.getExpectedStartTime();
        String date = start == null ? "" : "_" + start.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(vn.com.d2s.smr.config.BusinessTime.ZONE)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return new ExportedFile("ThamDu_" + meeting.getRoomCode() + date + ".xlsx", content);
    }

    /** Chủ trì của cuộc họp hoặc Quản trị viên (trang Thống kê P5). */
    private void checkAttendanceAccess(String userName, List<MeetingPersonal> personals) {
        boolean host = personals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!host && !adminAccess.isAdmin(userName)) {
            throw new SecurityException("Chỉ chủ trì hoặc quản trị viên được xem lịch sử tham dự.");
        }
    }

    // ===================== Màn /join/:roomCode =====================

    @Override
    @Transactional(readOnly = true)
    public PublicMeetingResponse publicInfo(String roomCode) {
        String code = roomCode == null ? "" : roomCode.trim();
        MeetingInfo m = code.isEmpty() ? null : meetingRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse(code).orElse(null);
        if (m == null || m.getStatus() == 0) {
            return PublicMeetingResponse.notFound();
        }
        String reason = switch (m.getStatus()) {
            case 4 -> "CANCELLED";
            case 3, 5 -> "ENDED";
            default -> null;
        };
        boolean guestAllowed = reason == null && m.getVisibility() == 2 && admission.settings(m).allowGuests();
        if (reason == null && !guestAllowed) {
            reason = "GUEST_NOT_ALLOWED";
        }
        if (!guestAllowed) {
            // Không lộ tên/giờ của cuộc họp không mở cho khách; người đã đăng nhập sẽ được chuyển sang luồng thành viên.
            return new PublicMeetingResponse(m.getId(), null, m.getStatus(), null, null, null, false, reason);
        }
        String hostName = personalRepository.findByMeetingId(m.getId()).stream()
                .filter(MeetingPersonal::isChairperson).map(MeetingPersonal::getFullName).findFirst().orElse("");
        return new PublicMeetingResponse(m.getId(), m.getName(), m.getStatus(), m.getExpectedStartTime(),
                m.getExpectedEndTime(), hostName, true, null);
    }

    // ===================== Tiện ích =====================

    private MeetingInfo meeting(String meetingId) {
        return meetingRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));
    }

    private MeetingPersonal member(String meetingId, String userName) {
        return personalRepository.findByMeetingIdAndUserName(meetingId, userName)
                .orElseThrow(() -> new SecurityException("Bạn không phải thành viên cuộc họp này."));
    }

    private MeetingPersonal moderator(String meetingId, String userName) {
        MeetingPersonal me = member(meetingId, userName);
        if (!RoomAdmission.isModerator(me)) {
            throw new SecurityException("Chỉ chủ trì được thực hiện thao tác này.");
        }
        return me;
    }

    private java.util.Optional<MtLobbyRequest> latestRequest(String meetingId, String userName) {
        return lobbyRepository.findFirstByMeetingIdAndUserNameOrderByRequestedAtDesc(meetingId, userName);
    }

    private static String message(RoomAdmission.State state) {
        return switch (state) {
            case ADMITTED -> null;
            case LOBBY -> "Đang chờ chủ trì cho vào phòng.";
            case REJECTED -> "Chủ trì chưa cho bạn vào phòng.";
            case WAIT_HOST -> "Cuộc họp chưa bắt đầu. Bạn sẽ được vào khi chủ trì bắt đầu.";
            case NOT_READY -> "Cuộc họp chưa được phát hành.";
            case ENDED -> "Cuộc họp đã kết thúc.";
            case CANCELLED -> "Cuộc họp đã bị hủy.";
            case NEED_REQUEST -> "Cần gửi yêu cầu vào phòng.";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
