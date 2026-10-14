package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.CancelMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.CreateMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.MeetingAuditResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDashboardResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDetailResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingJoinInfoResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingListItemResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingParticipantInputDto;
import vn.com.d2s.smr.dto.mt.meeting.MeetingParticipantResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSearchRequest;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSettingsDto;
import vn.com.d2s.smr.dto.mt.meeting.QuickMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.ScheduleConflictRequest;
import vn.com.d2s.smr.dto.mt.meeting.ScheduleConflictResponse;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingParticipantsRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingRequest;
import vn.com.d2s.smr.dto.mt.message.MeetingMessageResponse;
import vn.com.d2s.smr.dto.mt.message.SendMeetingMessageRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingAuditLog;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingMessage;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingAuditLogRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingMessageRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.mt.MeetingService;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;

import vn.com.d2s.smr.config.BusinessTime;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCodeLookupResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MeetingServiceImpl implements MeetingService {

    private final MeetingInfoRepository meetingInfoRepository;
    private final MeetingPersonalRepository meetingPersonalRepository;
    private final MeetingAuditLogRepository meetingAuditLogRepository;
    private final MeetingMessageRepository meetingMessageRepository;
    private final AdAccountRepository accountRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String jitsiDomain;
    private final String jitsiExternalApiUrl;
    /** Domain event cho thông báo; mặc định no-op để unit test không cần Spring context. */
    private ApplicationEventPublisher events = event -> { };

    @Autowired
    public MeetingServiceImpl(
            MeetingInfoRepository meetingInfoRepository,
            MeetingPersonalRepository meetingPersonalRepository,
            MeetingAuditLogRepository meetingAuditLogRepository,
            MeetingMessageRepository meetingMessageRepository,
            AdAccountRepository accountRepository,
            ObjectMapper objectMapper,
            @Value("${smr.jitsi.domain:meet.d2s.vn}") String jitsiDomain,
            @Value("${smr.jitsi.external-api-url:https://meet.d2s.vn/external_api.js}") String jitsiExternalApiUrl
    ) {
        this(
                meetingInfoRepository,
                meetingPersonalRepository,
                meetingAuditLogRepository,
                meetingMessageRepository,
                accountRepository,
                objectMapper,
                Clock.systemUTC(),
                jitsiDomain,
                jitsiExternalApiUrl
        );
    }

    MeetingServiceImpl(
            MeetingInfoRepository meetingInfoRepository,
            MeetingPersonalRepository meetingPersonalRepository,
            MeetingAuditLogRepository meetingAuditLogRepository,
            MeetingMessageRepository meetingMessageRepository,
            AdAccountRepository accountRepository,
            ObjectMapper objectMapper,
            Clock clock,
            String jitsiDomain,
            String jitsiExternalApiUrl
    ) {
        this.meetingInfoRepository = meetingInfoRepository;
        this.meetingPersonalRepository = meetingPersonalRepository;
        this.meetingAuditLogRepository = meetingAuditLogRepository;
        this.meetingMessageRepository = meetingMessageRepository;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.jitsiDomain = jitsiDomain;
        this.jitsiExternalApiUrl = jitsiExternalApiUrl;
    }

    @Autowired(required = false)
    void setEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** Realtime phòng họp (P10); mặc định no-op cho unit test. */
    private RoomEventPublisher room = RoomEventPublisher.NO_OP;
    /** Quy tắc phòng chờ (P10); null trong unit test cũ → coi như được vào. */
    private RoomAdmission admission;

    @Autowired(required = false)
    void setRoomPublisher(RoomEventPublisher room) {
        this.room = room;
    }

    @Autowired(required = false)
    void setRoomAdmission(RoomAdmission admission) {
        this.admission = admission;
    }

    private void publishStatus(String meetingId, int status, String action) {
        room.broadcastToRoom(meetingId, "MeetingStatusChanged", Map.of("meetingId", meetingId, "status", status, "action", action));
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingDashboardResponse getDashboard(String userName) {
        int upcoming = meetingInfoRepository.countByStatusAndUser(1, userName);
        int ongoing = meetingInfoRepository.countByStatusAndUser(2, userName);
        int ended = meetingInfoRepository.countByStatusAndUser(3, userName);
        int cancelled = meetingInfoRepository.countByStatusAndUser(4, userName);

        // Chỉ lấy cuộc họp từ đầu ngày hôm nay (giờ VN): cuộc họp quá giờ từ hôm trước mà chưa ai bắt đầu
        // không được chiếm chỗ đầu danh sách "sắp tới".
        List<MeetingInfo> nextMeetingsEntities = meetingInfoRepository.findNextMeetings(
                userName, BusinessTime.startOfTodayUtc(clock), PageRequest.of(0, 5));
        List<MeetingListItemResponse> nextMeetings = nextMeetingsEntities.stream()
                .map(m -> toListItemResponse(m, userName))
                .toList();

        return new MeetingDashboardResponse(upcoming, ongoing, ended, cancelled, nextMeetings);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingCodeLookupResponse lookupByRoomCode(String userName, String roomCode) {
        String code = roomCode == null ? "" : roomCode.trim();
        if (code.isEmpty()) {
            return MeetingCodeLookupResponse.notFound();
        }
        MeetingInfo meeting = meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse(code).orElse(null);
        if (meeting == null || meeting.getStatus() == 0) {
            // Bản nháp chưa phát hành → coi như không tồn tại.
            return MeetingCodeLookupResponse.notFound();
        }

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meeting.getId());
        boolean member = personals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(userName));
        String hostName = personals.stream()
                .filter(MeetingPersonal::isChairperson)
                .sorted(java.util.Comparator.comparingInt(MeetingPersonal::getType))
                .map(MeetingPersonal::getFullName)
                .findFirst()
                .orElse(meeting.getCreateBy());

        // Người chưa được mời: cuộc họp Nội bộ (1) / Công khai (2) đang hoặc sắp diễn ra → vào được
        // (P10: room/enter thêm họ làm thành viên "Tùy chọn"). "Chỉ người được mời" → chỉ biết lý do.
        boolean open = meeting.getVisibility() >= 1 && (meeting.getStatus() == 1 || meeting.getStatus() == 2);
        if (!member && !open) {
            return new MeetingCodeLookupResponse(null, null, -1, null, null, null, false, false, "NOT_INVITED");
        }
        String reason = switch (meeting.getStatus()) {
            case 4 -> "CANCELLED";
            case 3, 5 -> "ENDED";
            default -> null;
        };
        return new MeetingCodeLookupResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getStatus(),
                meeting.getExpectedStartTime(),
                meeting.getExpectedEndTime(),
                hostName,
                member,
                reason == null,
                reason
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResultResponse<MeetingListItemResponse> searchMeetings(String userName, MeetingSearchRequest request) {
        String tab = request.tab().toLowerCase(Locale.ROOT);
        List<Integer> statuses = statusesForTab(tab, request.status());
        // Sắp diễn ra: gần nhất lên đầu; các tab khác: mới nhất lên đầu.
        Sort sort = "upcoming".equals(tab)
                ? Sort.by(Sort.Direction.ASC, "expectedStartTime")
                : Sort.by(Sort.Direction.DESC, "expectedStartTime");

        Page<MeetingInfo> pageResult = meetingInfoRepository.searchMeetings(
                userName,
                statuses.isEmpty(),
                statuses.isEmpty() ? List.of(-1) : statuses,
                request.keyword(),
                request.startDate(),
                request.endDate(),
                PageRequest.of(request.page() - 1, request.pageSize(), sort)
        );

        List<MeetingListItemResponse> items = pageResult.getContent().stream()
                .map(m -> toListItemResponse(m, userName))
                .toList();

        return PagedResultResponse.of(
                items,
                request.page(),
                request.pageSize(),
                pageResult.getTotalElements()
        );
    }

    /**
     * Tab → các trạng thái được lấy. Rỗng = không lọc.
     * all: mọi trạng thái trừ đã lưu trữ · upcoming: 1 · ongoing: 2 · history: 3, 4 · archived: 5 · draft: 0.
     * {@code status} (nếu có) lọc thêm trong phạm vi tab.
     */
    static List<Integer> statusesForTab(String tab, Integer status) {
        List<Integer> base = switch (tab) {
            case "upcoming" -> List.of(1);
            case "ongoing" -> List.of(2);
            case "history" -> List.of(3, 4);
            case "archived" -> List.of(5);
            case "draft" -> List.of(0);
            default -> List.of(0, 1, 2, 3, 4);
        };
        if (status == null) {
            return base;
        }
        return base.contains(status) ? List.of(status) : List.of(-1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleConflictResponse> findScheduleConflicts(String userName, ScheduleConflictRequest request) {
        if (request == null || request.startTime() == null || request.endTime() == null
                || !request.endTime().isAfter(request.startTime())) {
            return List.of();
        }
        Set<String> users = new LinkedHashSet<>();
        users.add(userName.toLowerCase(Locale.ROOT));
        if (request.userNames() != null) {
            request.userNames().stream()
                    .filter(u -> u != null && !u.isBlank())
                    .map(u -> u.trim().toLowerCase(Locale.ROOT))
                    .forEach(users::add);
        }
        String exclude = request.excludeMeetingId() == null || request.excludeMeetingId().isBlank()
                ? null : request.excludeMeetingId();
        return meetingInfoRepository.findScheduleConflicts(users, request.startTime(), request.endTime(), exclude)
                .stream()
                .map(row -> {
                    MeetingInfo m = (MeetingInfo) row[0];
                    MeetingPersonal p = (MeetingPersonal) row[1];
                    return new ScheduleConflictResponse(m.getId(), m.getName(), m.getExpectedStartTime(),
                            m.getExpectedEndTime(), m.getStatus(), p.getUserName(), p.getFullName());
                })
                .toList();
    }

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MeetingDetailResponse> getMeetingDetail(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId).orElse(null);
        if (meeting == null) {
            return Optional.empty();
        }

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isMember = personals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(userName));
        if (!isMember) {
            throw new SecurityException("Bạn không có quyền truy cập thông tin cuộc họp này.");
        }

        return Optional.of(toDetailResponse(meeting, personals, userName));
    }

    @Override
    @Transactional
    public MeetingDetailResponse createMeeting(String userName, CreateMeetingRequest request) {
        AdAccount hostAccount = accountRepository.findById(userName)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản người tạo cuộc họp không tồn tại."));

        validateTimeRange(request.expectedStartTime(), request.expectedEndTime());
        String meetingId = UUID.randomUUID().toString().replace("-", "");
        String refFileId = UUID.randomUUID().toString().replace("-", "");
        String roomCode = generateRoomCode();
        LocalDateTime now = LocalDateTime.now(clock);

        MeetingInfo meeting = new MeetingInfo();
        meeting.setId(meetingId);
        meeting.setName(request.name().trim());
        meeting.setMeetContent(request.description() != null ? request.description().trim() : "");
        meeting.setAgenda(request.agenda() != null ? request.agenda().trim() : "");
        meeting.setExpectedStartTime(request.expectedStartTime() != null ? request.expectedStartTime() : now);
        meeting.setExpectedEndTime(request.expectedEndTime() != null ? request.expectedEndTime() : now.plusHours(1));
        meeting.setTimeZone(request.timeZone() != null ? request.timeZone().trim() : "Asia/Bangkok");
        meeting.setStatus(request.saveAsDraft() ? 0 : 1);
        meeting.setVisibility(request.visibility());
        meeting.setRoomCode(roomCode);
        meeting.setJoinUrl("/meeting/" + roomCode);
        meeting.setReferenceFileId(refFileId);
        meeting.setNotes("");
        meeting.setSettingsJson(toJson(request.settings()));
        meeting.setDraft(request.saveAsDraft());
        meeting.setArchived(false);
        meeting.setDeleted(false);
        meeting.setVersion(1);
        meeting.setCreateBy(userName);
        meeting.setCreateDate(now);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);

        meetingInfoRepository.save(meeting);

        List<MeetingPersonal> personals = new ArrayList<>();

        MeetingPersonal hostPersonal = new MeetingPersonal();
        hostPersonal.setId(UUID.randomUUID().toString().replace("-", ""));
        hostPersonal.setMeetingId(meetingId);
        hostPersonal.setUserName(hostAccount.getUserName());
        hostPersonal.setFullName(hostAccount.getFullName());
        hostPersonal.setPhone(hostAccount.getPhone() != null ? hostAccount.getPhone() : "");
        hostPersonal.setEmail(hostAccount.getEmail() != null ? hostAccount.getEmail() : "");
        hostPersonal.setAddress(hostAccount.getAddress() != null ? hostAccount.getAddress() : "");
        hostPersonal.setOrgId(hostAccount.getOrgId() != null ? hostAccount.getOrgId() : "");
        hostPersonal.setTitleCode(hostAccount.getTitleCode() != null ? hostAccount.getTitleCode() : "");
        hostPersonal.setReferenceFileId(refFileId);
        hostPersonal.setType(1);
        hostPersonal.setChairperson(true);
        hostPersonal.setJoined(false);
        hostPersonal.setCreateBy(userName);
        hostPersonal.setCreateDate(now);
        hostPersonal.setUpdateBy(userName);
        hostPersonal.setUpdateDate(now);
        personals.add(hostPersonal);

        if (request.participants() != null) {
            for (MeetingParticipantInputDto pDto : request.participants()) {
                if (pDto.userName().equalsIgnoreCase(userName)) {
                    continue;
                }
                AdAccount pAccount = accountRepository.findById(pDto.userName()).orElse(null);
                MeetingPersonal p = new MeetingPersonal();
                p.setId(UUID.randomUUID().toString().replace("-", ""));
                p.setMeetingId(meetingId);
                p.setUserName(pDto.userName());
                p.setFullName(pAccount != null ? pAccount.getFullName() : pDto.userName());
                p.setPhone(pAccount != null && pAccount.getPhone() != null ? pAccount.getPhone() : "");
                p.setEmail(pAccount != null && pAccount.getEmail() != null ? pAccount.getEmail() : "");
                p.setAddress(pAccount != null && pAccount.getAddress() != null ? pAccount.getAddress() : "");
                p.setOrgId(pAccount != null && pAccount.getOrgId() != null ? pAccount.getOrgId() : "");
                p.setTitleCode(pAccount != null && pAccount.getTitleCode() != null ? pAccount.getTitleCode() : "");
                p.setReferenceFileId(refFileId);
                p.setType(RoomAdmission.invitedRole(pDto.role()));
                p.setChairperson(p.getType() == 2);
                p.setJoined(false);
                p.setCreateBy(userName);
                p.setCreateDate(now);
                p.setUpdateBy(userName);
                p.setUpdateDate(now);
                personals.add(p);
            }
        }

        if (request.participantUserNames() != null) {
            for (String pUser : request.participantUserNames()) {
                if (pUser.equalsIgnoreCase(userName) || personals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(pUser))) {
                    continue;
                }
                AdAccount pAccount = accountRepository.findById(pUser).orElse(null);
                MeetingPersonal p = new MeetingPersonal();
                p.setId(UUID.randomUUID().toString().replace("-", ""));
                p.setMeetingId(meetingId);
                p.setUserName(pUser);
                p.setFullName(pAccount != null ? pAccount.getFullName() : pUser);
                p.setPhone(pAccount != null && pAccount.getPhone() != null ? pAccount.getPhone() : "");
                p.setEmail(pAccount != null && pAccount.getEmail() != null ? pAccount.getEmail() : "");
                p.setAddress(pAccount != null && pAccount.getAddress() != null ? pAccount.getAddress() : "");
                p.setOrgId(pAccount != null && pAccount.getOrgId() != null ? pAccount.getOrgId() : "");
                p.setTitleCode(pAccount != null && pAccount.getTitleCode() != null ? pAccount.getTitleCode() : "");
                p.setReferenceFileId(refFileId);
                p.setType(4);
                p.setChairperson(false);
                p.setJoined(false);
                p.setCreateBy(userName);
                p.setCreateDate(now);
                p.setUpdateBy(userName);
                p.setUpdateDate(now);
                personals.add(p);
            }
        }

        meetingPersonalRepository.saveAll(personals);

        saveAuditLog(meetingId, "CREATE", userName, 1, request.saveAsDraft() ? "Lưu nháp cuộc họp." : "Tạo cuộc họp mới.");
        // Bản nháp chưa gửi lời mời; gửi khi phát hành (updateMeeting với publish = true).
        if (!request.saveAsDraft()) {
            publishInvited(meetingId, userName, personals);
        }

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public MeetingDetailResponse updateMeeting(String userName, String meetingId, UpdateMeetingRequest request) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHostOrManager = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHostOrManager) {
            throw new SecurityException("Bạn không có quyền cập nhật cuộc họp này.");
        }

        if (meeting.getStatus() == 3 || meeting.getStatus() == 4) {
            throw new IllegalStateException("Không thể cập nhật cuộc họp đã kết thúc hoặc đã bị hủy.");
        }

        if (request.rowVersion() != null && !request.rowVersion().isBlank()) {
            String currentRowVersionHex = formatRowVersion(meeting.getRowVersion());
            if (!currentRowVersionHex.equalsIgnoreCase(request.rowVersion().trim())) {
                throw new IllegalStateException("Dữ liệu đã được thay đổi bởi phiên làm việc khác. Vui lòng làm mới trang.");
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime oldStart = meeting.getExpectedStartTime();
        LocalDateTime oldEnd = meeting.getExpectedEndTime();
        validateTimeRange(
                request.expectedStartTime() != null ? request.expectedStartTime() : oldStart,
                request.expectedEndTime() != null ? request.expectedEndTime() : oldEnd);
        boolean publishing = request.publish() && meeting.getStatus() == 0;

        meeting.setName(request.name().trim());
        if (request.description() != null) {
            meeting.setMeetContent(request.description().trim());
        }
        if (request.agenda() != null) {
            meeting.setAgenda(request.agenda().trim());
        }
        if (request.expectedStartTime() != null) {
            meeting.setExpectedStartTime(request.expectedStartTime());
        }
        if (request.expectedEndTime() != null) {
            meeting.setExpectedEndTime(request.expectedEndTime());
        }
        if (request.timeZone() != null) {
            meeting.setTimeZone(request.timeZone().trim());
        }
        meeting.setVisibility(request.visibility());
        if (request.settings() != null) {
            meeting.setSettingsJson(toJson(request.settings()));
        }
        if (request.meetContent() != null) {
            meeting.setMeetContent(request.meetContent().trim());
        }
        if (request.notes() != null) {
            meeting.setNotes(request.notes().trim());
        }

        if (publishing) {
            meeting.setStatus(1);
            meeting.setDraft(false);
        }

        meeting.setVersion(meeting.getVersion() + 1);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);

        meetingInfoRepository.save(meeting);

        if (publishing) {
            saveAuditLog(meetingId, "PUBLISH", userName, meeting.getVersion(), "Phát hành cuộc họp và gửi lời mời.");
            publishInvited(meetingId, userName, personals);
        } else {
            saveAuditLog(meetingId, "UPDATE", userName, meeting.getVersion(), "Cập nhật thông tin cuộc họp.");
            boolean timeChanged = !Objects.equals(oldStart, meeting.getExpectedStartTime())
                    || !Objects.equals(oldEnd, meeting.getExpectedEndTime());
            // Bản nháp chưa ai được mời → không cần báo đổi giờ.
            if (timeChanged && meeting.getStatus() != 0) {
                events.publishEvent(new NotificationEvents.MeetingRescheduled(meetingId, userName));
            }
        }

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public void deleteMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền xóa cuộc họp này.");
        }

        // Chỉ xóa bản nháp; cuộc họp đã gửi lời mời thì phải hủy (kèm lý do) để người được mời nhận thông báo.
        if (meeting.getStatus() != 0 || meeting.getStartDate() != null) {
            throw new IllegalStateException("Chỉ xóa được cuộc họp nháp. Cuộc họp đã gửi lời mời hãy dùng chức năng Hủy.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setDeleted(true);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "DELETE", userName, meeting.getVersion() + 1, "Xóa cuộc họp.");
    }

    @Override
    @Transactional
    public void cancelMeeting(String userName, String meetingId, CancelMeetingRequest request) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền hủy cuộc họp này.");
        }

        if (meeting.getStatus() != 0 && meeting.getStatus() != 1) {
            throw new IllegalStateException("Chỉ hủy được cuộc họp chưa bắt đầu.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setStatus(4);
        meeting.setCancellationReason(request.reason().trim());
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "CANCEL", userName, meeting.getVersion() + 1, "Hủy cuộc họp: " + request.reason().trim());
        events.publishEvent(new NotificationEvents.MeetingCancelled(meetingId, userName, request.reason().trim()));
        publishStatus(meetingId, 4, "cancelled");
    }

    @Override
    @Transactional
    public void archiveMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền lưu trữ cuộc họp này.");
        }

        if (meeting.getStatus() != 3 && meeting.getStatus() != 4) {
            throw new IllegalStateException("Chỉ có thể lưu trữ cuộc họp đã kết thúc hoặc đã bị hủy.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setArchived(true);
        meeting.setStatus(5);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "ARCHIVE", userName, meeting.getVersion() + 1, "Lưu trữ cuộc họp.");
    }

    @Override
    @Transactional
    public MeetingDetailResponse createQuickMeeting(String userName, QuickMeetingRequest request) {
        AdAccount hostAccount = accountRepository.findById(userName)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản người tạo cuộc họp không tồn tại."));

        String meetingId = UUID.randomUUID().toString().replace("-", "");
        String refFileId = UUID.randomUUID().toString().replace("-", "");
        String roomCode = generateRoomCode();
        LocalDateTime now = LocalDateTime.now(clock);

        String meetingName = (request.name() != null && !request.name().isBlank())
                ? request.name().trim()
                : "Cuộc họp nhanh - " + hostAccount.getFullName();

        MeetingInfo meeting = new MeetingInfo();
        meeting.setId(meetingId);
        meeting.setName(meetingName);
        meeting.setMeetContent("Tạo nhanh bởi " + hostAccount.getFullName());
        meeting.setAgenda("");
        meeting.setExpectedStartTime(now);
        meeting.setStartDate(now);
        meeting.setExpectedEndTime(now.plusHours(1));
        meeting.setTimeZone("Asia/Bangkok");
        meeting.setStatus(2);
        meeting.setVisibility(1);
        meeting.setRoomCode(roomCode);
        meeting.setJoinUrl("/meeting/" + roomCode);
        meeting.setReferenceFileId(refFileId);
        meeting.setNotes("");
        meeting.setSettingsJson("{\"schemaVersion\":1}");
        meeting.setDraft(false);
        meeting.setArchived(false);
        meeting.setDeleted(false);
        meeting.setVersion(1);
        meeting.setCreateBy(userName);
        meeting.setCreateDate(now);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);

        meetingInfoRepository.save(meeting);

        List<MeetingPersonal> personals = new ArrayList<>();

        MeetingPersonal hostPersonal = new MeetingPersonal();
        hostPersonal.setId(UUID.randomUUID().toString().replace("-", ""));
        hostPersonal.setMeetingId(meetingId);
        hostPersonal.setUserName(hostAccount.getUserName());
        hostPersonal.setFullName(hostAccount.getFullName());
        hostPersonal.setPhone(hostAccount.getPhone() != null ? hostAccount.getPhone() : "");
        hostPersonal.setEmail(hostAccount.getEmail() != null ? hostAccount.getEmail() : "");
        hostPersonal.setAddress(hostAccount.getAddress() != null ? hostAccount.getAddress() : "");
        hostPersonal.setOrgId(hostAccount.getOrgId() != null ? hostAccount.getOrgId() : "");
        hostPersonal.setTitleCode(hostAccount.getTitleCode() != null ? hostAccount.getTitleCode() : "");
        hostPersonal.setReferenceFileId(refFileId);
        hostPersonal.setType(1);
        hostPersonal.setChairperson(true);
        hostPersonal.setJoined(true);
        hostPersonal.setJoinTime(now);
        hostPersonal.setCreateBy(userName);
        hostPersonal.setCreateDate(now);
        hostPersonal.setUpdateBy(userName);
        hostPersonal.setUpdateDate(now);
        personals.add(hostPersonal);

        if (request.participantUserNames() != null) {
            for (String pUser : request.participantUserNames()) {
                if (pUser.equalsIgnoreCase(userName)) {
                    continue;
                }
                AdAccount pAccount = accountRepository.findById(pUser).orElse(null);
                MeetingPersonal p = new MeetingPersonal();
                p.setId(UUID.randomUUID().toString().replace("-", ""));
                p.setMeetingId(meetingId);
                p.setUserName(pUser);
                p.setFullName(pAccount != null ? pAccount.getFullName() : pUser);
                p.setPhone(pAccount != null && pAccount.getPhone() != null ? pAccount.getPhone() : "");
                p.setEmail(pAccount != null && pAccount.getEmail() != null ? pAccount.getEmail() : "");
                p.setAddress(pAccount != null && pAccount.getAddress() != null ? pAccount.getAddress() : "");
                p.setOrgId(pAccount != null && pAccount.getOrgId() != null ? pAccount.getOrgId() : "");
                p.setTitleCode(pAccount != null && pAccount.getTitleCode() != null ? pAccount.getTitleCode() : "");
                p.setReferenceFileId(refFileId);
                p.setType(4);
                p.setChairperson(false);
                p.setJoined(false);
                p.setCreateBy(userName);
                p.setCreateDate(now);
                p.setUpdateBy(userName);
                p.setUpdateDate(now);
                personals.add(p);
            }
        }

        meetingPersonalRepository.saveAll(personals);

        saveAuditLog(meetingId, "CREATE_QUICK", userName, 1, "Tạo cuộc họp nhanh.");
        publishInvited(meetingId, userName, personals);

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public MeetingDetailResponse addParticipants(String userName, String meetingId, UpdateMeetingParticipantsRequest request) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = new ArrayList<>(meetingPersonalRepository.findByMeetingId(meetingId));
        boolean isHostOrManager = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHostOrManager) {
            throw new SecurityException("Bạn không có quyền quản lý thành viên cuộc họp này.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<MeetingPersonal> newPersonals = new ArrayList<>();

        if (request.participants() != null) {
            for (MeetingParticipantInputDto pDto : request.participants()) {
                if (personals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(pDto.userName()))) {
                    continue;
                }
                AdAccount pAccount = accountRepository.findById(pDto.userName()).orElse(null);
                MeetingPersonal p = new MeetingPersonal();
                p.setId(UUID.randomUUID().toString().replace("-", ""));
                p.setMeetingId(meetingId);
                p.setUserName(pDto.userName());
                p.setFullName(pAccount != null ? pAccount.getFullName() : pDto.userName());
                p.setPhone(pAccount != null && pAccount.getPhone() != null ? pAccount.getPhone() : "");
                p.setEmail(pAccount != null && pAccount.getEmail() != null ? pAccount.getEmail() : "");
                p.setAddress(pAccount != null && pAccount.getAddress() != null ? pAccount.getAddress() : "");
                p.setOrgId(pAccount != null && pAccount.getOrgId() != null ? pAccount.getOrgId() : "");
                p.setTitleCode(pAccount != null && pAccount.getTitleCode() != null ? pAccount.getTitleCode() : "");
                p.setReferenceFileId(meeting.getReferenceFileId());
                p.setType(RoomAdmission.invitedRole(pDto.role()));
                p.setChairperson(p.getType() == 2);
                p.setJoined(false);
                p.setCreateBy(userName);
                p.setCreateDate(now);
                p.setUpdateBy(userName);
                p.setUpdateDate(now);
                newPersonals.add(p);
            }
        }

        if (request.participantUserNames() != null) {
            for (String pUser : request.participantUserNames()) {
                if (personals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(pUser))
                        || newPersonals.stream().anyMatch(p -> p.getUserName().equalsIgnoreCase(pUser))) {
                    continue;
                }
                AdAccount pAccount = accountRepository.findById(pUser).orElse(null);
                MeetingPersonal p = new MeetingPersonal();
                p.setId(UUID.randomUUID().toString().replace("-", ""));
                p.setMeetingId(meetingId);
                p.setUserName(pUser);
                p.setFullName(pAccount != null ? pAccount.getFullName() : pUser);
                p.setPhone(pAccount != null && pAccount.getPhone() != null ? pAccount.getPhone() : "");
                p.setEmail(pAccount != null && pAccount.getEmail() != null ? pAccount.getEmail() : "");
                p.setAddress(pAccount != null && pAccount.getAddress() != null ? pAccount.getAddress() : "");
                p.setOrgId(pAccount != null && pAccount.getOrgId() != null ? pAccount.getOrgId() : "");
                p.setTitleCode(pAccount != null && pAccount.getTitleCode() != null ? pAccount.getTitleCode() : "");
                p.setReferenceFileId(meeting.getReferenceFileId());
                p.setType(4);
                p.setChairperson(false);
                p.setJoined(false);
                p.setCreateBy(userName);
                p.setCreateDate(now);
                p.setUpdateBy(userName);
                p.setUpdateDate(now);
                newPersonals.add(p);
            }
        }

        if (!newPersonals.isEmpty()) {
            meetingPersonalRepository.saveAll(newPersonals);
            personals.addAll(newPersonals);
            saveAuditLog(meetingId, "ADD_PARTICIPANTS", userName, meeting.getVersion(), "Thêm thành viên vào cuộc họp.");
            publishInvited(meetingId, userName, newPersonals);
        }

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public void removeParticipant(String userName, String meetingId, String participantUserName) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHostOrManager = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHostOrManager) {
            throw new SecurityException("Bạn không có quyền xóa thành viên khỏi cuộc họp này.");
        }

        MeetingPersonal target = personals.stream()
                .filter(p -> p.getUserName().equalsIgnoreCase(participantUserName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Thành viên không tồn tại trong cuộc họp."));

        if (RoomAdmission.isOwner(target)) {
            throw new IllegalStateException("Không thể xóa chủ trì (người tạo) khỏi cuộc họp.");
        }

        meetingPersonalRepository.deleteByMeetingIdAndUserName(meetingId, target.getUserName());
        saveAuditLog(meetingId, "REMOVE_PARTICIPANT", userName, meeting.getVersion(), "Xóa thành viên: " + participantUserName);
        events.publishEvent(new NotificationEvents.MeetingParticipantRemoved(meetingId, meeting.getName(), userName, target.getUserName()));
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingJoinInfoResponse getJoinInfo(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        MeetingPersonal personal = personals.stream()
                .filter(p -> p.getUserName().equalsIgnoreCase(userName))
                .findFirst()
                .orElseThrow(() -> new SecurityException("Bạn không có quyền tham gia cuộc họp này."));

        if (meeting.getStatus() == 4) {
            throw new IllegalStateException("Cuộc họp đã bị hủy.");
        }
        // Phải qua phòng chờ (room/enter) mới lấy được thông tin vào phòng Jitsi.
        if (admission != null) {
            RoomAdmission.State state = admission.evaluate(meeting, personal);
            if (state != RoomAdmission.State.ADMITTED) {
                throw new IllegalStateException("Chưa được vào phòng (" + state.name() + ").");
            }
        }

        boolean isHost = RoomAdmission.isModerator(personal);
        // Type 3 là Thư ký — khách nhận biết bằng tên đăng nhập "guest_xxx".
        boolean isGuest = RoomAdmission.isGuest(userName);

        return new MeetingJoinInfoResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getRoomCode(),
                meeting.getJoinUrl(),
                meeting.getStatus(),
                meeting.getExpectedStartTime(),
                meeting.getExpectedEndTime(),
                jitsiDomain,
                meeting.getRoomCode(),
                personal.getFullName(),
                isHost,
                isGuest,
                jitsiDomain,
                isHost,
                false,
                false,
                jitsiExternalApiUrl
        );
    }

    @Override
    @Transactional
    public void startMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền bắt đầu cuộc họp này.");
        }

        if (meeting.getStatus() == 2) {
            return; // đã bắt đầu (bấm hai lần / hai chủ trì cùng bấm)
        }
        if (meeting.getStatus() != 1) {
            throw new IllegalStateException("Chỉ bắt đầu được cuộc họp đã lên lịch.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setStatus(2);
        meeting.setStartDate(now);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "START", userName, meeting.getVersion() + 1, "Bắt đầu cuộc họp.");
        events.publishEvent(new NotificationEvents.MeetingStarted(meetingId, userName));
        publishStatus(meetingId, 2, "started");
    }

    @Override
    @Transactional
    public void endMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền kết thúc cuộc họp này.");
        }

        if (meeting.getStatus() == 3) {
            return; // đã kết thúc (bấm hai lần / hai chủ trì cùng bấm)
        }
        if (meeting.getStatus() != 2) {
            throw new IllegalStateException("Chỉ kết thúc được cuộc họp đang diễn ra.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setStatus(3);
        meeting.setEndDate(now);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);
        // Mọi người coi như đã rời phòng.
        List<MeetingPersonal> stillIn = meetingPersonalRepository.findByMeetingId(meetingId).stream()
                .filter(MeetingPersonal::isJoined).toList();
        stillIn.forEach(p -> p.setJoined(false));
        if (!stillIn.isEmpty()) {
            meetingPersonalRepository.saveAll(stillIn);
        }

        saveAuditLog(meetingId, "END", userName, meeting.getVersion() + 1, "Kết thúc cuộc họp.");
        publishStatus(meetingId, 3, "ended");
    }

    @Override
    @Transactional
    public void joinMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        MeetingPersonal personal = meetingPersonalRepository.findByMeetingIdAndUserName(meetingId, userName)
                .orElseThrow(() -> new SecurityException("Bạn không có quyền tham gia cuộc họp này."));

        if (meeting.getStatus() == 4) {
            throw new IllegalStateException("Cuộc họp đã bị hủy.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        personal.setJoined(true);
        personal.setJoinTime(now);
        personal.setUpdateBy(userName);
        personal.setUpdateDate(now);
        meetingPersonalRepository.save(personal);

        saveAuditLog(meetingId, "JOIN", userName, meeting.getVersion(), "Tham gia cuộc họp.");
    }

    @Override
    @Transactional
    public void leaveMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        MeetingPersonal personal = meetingPersonalRepository.findByMeetingIdAndUserName(meetingId, userName)
                .orElseThrow(() -> new SecurityException("Bạn không có trong cuộc họp này."));

        LocalDateTime now = LocalDateTime.now(clock);
        personal.setJoined(false);
        personal.setUpdateBy(userName);
        personal.setUpdateDate(now);
        meetingPersonalRepository.save(personal);

        saveAuditLog(meetingId, "LEAVE", userName, meeting.getVersion(), "Rời cuộc họp.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingMessageResponse> getMessages(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        boolean isMember = meetingPersonalRepository.existsByMeetingIdAndUserName(meetingId, userName);
        if (!isMember) {
            throw new SecurityException("Bạn không có quyền xem tin nhắn cuộc họp này.");
        }

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        Map<String, String> userNameToFullName = personals.stream()
                .collect(Collectors.toMap(MeetingPersonal::getUserName, MeetingPersonal::getFullName, (a, b) -> a));

        // Tin chung (ReceiverUserId rỗng) + tin riêng mình gửi / nhận.
        List<MeetingMessage> messages = meetingMessageRepository.findByMeetingIdOrderByCreateDateAsc(meetingId).stream()
                .filter(m -> m.getReceiverUserId() == null || m.getReceiverUserId().isBlank()
                        || m.getReceiverUserId().equalsIgnoreCase(userName)
                        || m.getSenderUserId().equalsIgnoreCase(userName))
                .toList();
        return messages.stream()
                .map(m -> new MeetingMessageResponse(
                        m.getId(),
                        m.getMeetingId(),
                        m.getSenderUserId(),
                        userNameToFullName.getOrDefault(m.getSenderUserId(), m.getSenderUserId()),
                        m.getReceiverUserId(),
                        m.getMessageText(),
                        m.getCreateDate()
                ))
                .toList();
    }

    @Override
    @Transactional
    public MeetingMessageResponse sendMessage(String userName, String meetingId, SendMeetingMessageRequest request) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        MeetingPersonal personal = meetingPersonalRepository.findByMeetingIdAndUserName(meetingId, userName)
                .orElseThrow(() -> new SecurityException("Bạn không có quyền gửi tin nhắn trong cuộc họp này."));
        if (!RoomAdmission.isModerator(personal) && !parseSettings(meeting.getSettingsJson()).chatEnabled()) {
            throw new SecurityException("Chủ trì đã tắt chat trong cuộc họp này.");
        }
        String receiver = request.receiverUserId() != null ? request.receiverUserId().trim() : "";
        if (!receiver.isEmpty()) {
            if (RoomAdmission.isGuest(userName)) {
                throw new SecurityException("Khách chỉ gửi được tin nhắn chung.");
            }
            if (!meetingPersonalRepository.existsByMeetingIdAndUserName(meetingId, receiver)) {
                throw new IllegalArgumentException("Người nhận không thuộc cuộc họp.");
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String messageId = UUID.randomUUID().toString().replace("-", "");

        MeetingMessage msg = new MeetingMessage();
        msg.setId(messageId);
        msg.setMeetingId(meetingId);
        msg.setSenderUserId(userName);
        msg.setReceiverUserId(receiver);
        msg.setMessageText(request.messageText().trim());
        msg.setCreateBy(userName);
        msg.setCreateDate(now);
        msg.setUpdateBy(userName);
        msg.setUpdateDate(now);

        meetingMessageRepository.save(msg);

        MeetingMessageResponse response = new MeetingMessageResponse(
                msg.getId(),
                msg.getMeetingId(),
                msg.getSenderUserId(),
                personal.getFullName(),
                msg.getReceiverUserId(),
                msg.getMessageText(),
                msg.getCreateDate()
        );
        if (receiver.isEmpty()) {
            room.broadcastToRoom(meetingId, "ReceiveMessage", response);
        } else {
            room.sendToUsers(List.of(userName, receiver), "ReceiveMessage", response);
        }
        return response;
    }

    private MeetingListItemResponse toListItemResponse(MeetingInfo meeting, String userName) {
        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meeting.getId());
        MeetingPersonal host = personals.stream()
                .filter(MeetingPersonal::isChairperson)
                .min(java.util.Comparator.comparingInt(MeetingPersonal::getType))
                .orElse(null);
        String hostName = host != null ? host.getFullName() : (meeting.getCreateBy() != null ? meeting.getCreateBy() : "");
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));

        return new MeetingListItemResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getMeetContent(),
                meeting.getExpectedStartTime(),
                meeting.getExpectedEndTime(),
                meeting.getStatus(),
                meeting.getVisibility(),
                meeting.getRoomCode(),
                meeting.getJoinUrl(),
                personals.size(),
                isHost,
                hostName
        );
    }

    private MeetingDetailResponse toDetailResponse(MeetingInfo meeting, List<MeetingPersonal> personals, String userName) {
        MeetingPersonal host = personals.stream()
                .filter(MeetingPersonal::isChairperson)
                .min(java.util.Comparator.comparingInt(MeetingPersonal::getType))
                .orElse(null);
        String hostName = host != null ? host.getFullName() : "";
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && RoomAdmission.isModerator(p));

        List<MeetingParticipantResponse> participantResponses = personals.stream()
                .map(p -> new MeetingParticipantResponse(
                        p.getUserName(),
                        p.getFullName(),
                        p.getEmail(),
                        p.getOrgId(),
                        p.getTitleCode(),
                        p.getType(),
                        p.isJoined(),
                        p.getJoinTime()
                ))
                .toList();

        List<MeetingAuditLog> auditLogs = meetingAuditLogRepository.findByMeetingIdOrderByOccurredAtDesc(meeting.getId());
        List<MeetingAuditResponse> auditResponses = auditLogs.stream()
                .map(a -> new MeetingAuditResponse(
                        a.getId(),
                        a.getAction(),
                        a.getActorId(),
                        a.getOccurredAt(),
                        a.getVersion(),
                        a.getPayloadJson()
                ))
                .toList();

        MeetingSettingsDto settings = parseSettings(meeting.getSettingsJson());
        String rowVersionHex = formatRowVersion(meeting.getRowVersion());

        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getMeetContent(),
                meeting.getExpectedStartTime(),
                meeting.getExpectedEndTime(),
                meeting.getStatus(),
                meeting.getVisibility(),
                meeting.getRoomCode(),
                meeting.getJoinUrl(),
                personals.size(),
                isHost,
                hostName,
                meeting.getAgenda(),
                meeting.getTimeZone(),
                meeting.getCancellationReason(),
                settings,
                participantResponses,
                auditResponses,
                rowVersionHex,
                isHost,
                meeting.getStartDate(),
                meeting.getEndDate()
        );
    }

    private void publishInvited(String meetingId, String actor, List<MeetingPersonal> invited) {
        List<String> userNames = invited.stream()
                .map(MeetingPersonal::getUserName)
                .filter(u -> !u.equalsIgnoreCase(actor))
                .toList();
        if (!userNames.isEmpty()) {
            events.publishEvent(new NotificationEvents.MeetingInvited(meetingId, actor, userNames));
        }
    }

    private void saveAuditLog(String meetingId, String action, String actorId, int version, String payload) {
        MeetingAuditLog log = new MeetingAuditLog();
        log.setId(UUID.randomUUID().toString().replace("-", ""));
        log.setMeetingId(meetingId);
        log.setAction(action);
        log.setActorId(actorId);
        log.setOccurredAt(LocalDateTime.now(clock));
        log.setCorrelationId(UUID.randomUUID().toString());
        log.setVersion(version);
        log.setPayloadJson(payload);
        meetingAuditLogRepository.save(log);
    }

    private String generateRoomCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8);
        } while (meetingInfoRepository.existsByRoomCode(code));
        return code;
    }

    private String formatRowVersion(byte[] rowVersion) {
        if (rowVersion == null || rowVersion.length == 0) {
            return "0x0000000000000000";
        }
        return "0x" + HexFormat.of().formatHex(rowVersion);
    }

    private String toJson(MeetingSettingsDto settings) {
        if (settings == null) {
            return "{\"schemaVersion\":1}";
        }
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            return "{\"schemaVersion\":1}";
        }
    }

    private MeetingSettingsDto parseSettings(String json) {
        return MeetingSettingsDto.fromJson(json, objectMapper);
    }
}
