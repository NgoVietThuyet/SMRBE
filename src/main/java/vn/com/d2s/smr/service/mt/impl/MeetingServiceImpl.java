package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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

    @Override
    @Transactional(readOnly = true)
    public MeetingDashboardResponse getDashboard(String userName) {
        int upcoming = meetingInfoRepository.countByStatusAndUser(1, userName);
        int ongoing = meetingInfoRepository.countByStatusAndUser(2, userName);
        int ended = meetingInfoRepository.countByStatusAndUser(3, userName);
        int cancelled = meetingInfoRepository.countByStatusAndUser(4, userName);

        List<MeetingInfo> nextMeetingsEntities = meetingInfoRepository.findNextMeetings(userName, PageRequest.of(0, 5));
        List<MeetingListItemResponse> nextMeetings = nextMeetingsEntities.stream()
                .map(m -> toListItemResponse(m, userName))
                .toList();

        return new MeetingDashboardResponse(upcoming, ongoing, ended, cancelled, nextMeetings);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResultResponse<MeetingListItemResponse> searchMeetings(String userName, MeetingSearchRequest request) {
        Integer statusFilter = switch (request.tab().toLowerCase()) {
            case "upcoming" -> 1;
            case "ongoing" -> 2;
            case "history" -> request.status() != null ? request.status() : null;
            default -> request.status();
        };

        Page<MeetingInfo> pageResult = meetingInfoRepository.searchMeetings(
                userName,
                statusFilter,
                request.keyword(),
                request.startDate(),
                request.endDate(),
                PageRequest.of(request.page() - 1, request.pageSize())
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
                p.setType(pDto.role() > 0 ? pDto.role() : 2);
                p.setChairperson(pDto.role() == 1);
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
                p.setType(2);
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

        saveAuditLog(meetingId, "CREATE", userName, 1, "Tạo cuộc họp mới.");

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public MeetingDetailResponse updateMeeting(String userName, String meetingId, UpdateMeetingRequest request) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHostOrManager = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && (p.isChairperson() || p.getType() == 1));
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

        meeting.setVersion(meeting.getVersion() + 1);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);

        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "UPDATE", userName, meeting.getVersion(), "Cập nhật thông tin cuộc họp.");

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public void deleteMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền xóa cuộc họp này.");
        }

        if (meeting.getStatus() == 2 || meeting.getStatus() == 3) {
            throw new IllegalStateException("Không thể xóa cuộc họp đang diễn ra hoặc đã kết thúc.");
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
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền hủy cuộc họp này.");
        }

        if (meeting.getStatus() == 3 || meeting.getStatus() == 4) {
            throw new IllegalStateException("Cuộc họp đã kết thúc hoặc đã bị hủy.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setStatus(4);
        meeting.setCancellationReason(request.reason().trim());
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "CANCEL", userName, meeting.getVersion() + 1, "Hủy cuộc họp: " + request.reason().trim());
    }

    @Override
    @Transactional
    public void archiveMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền lưu trữ cuộc họp này.");
        }

        if (meeting.getStatus() == 1 || meeting.getStatus() == 2) {
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
                p.setType(2);
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

        return toDetailResponse(meeting, personals, userName);
    }

    @Override
    @Transactional
    public MeetingDetailResponse addParticipants(String userName, String meetingId, UpdateMeetingParticipantsRequest request) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = new ArrayList<>(meetingPersonalRepository.findByMeetingId(meetingId));
        boolean isHostOrManager = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && (p.isChairperson() || p.getType() == 1));
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
                p.setType(pDto.role() > 0 ? pDto.role() : 2);
                p.setChairperson(pDto.role() == 1);
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
                p.setType(2);
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
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && (p.isChairperson() || p.getType() == 1));
        if (!isHostOrManager) {
            throw new SecurityException("Bạn không có quyền xóa thành viên khỏi cuộc họp này.");
        }

        MeetingPersonal target = personals.stream()
                .filter(p -> p.getUserName().equalsIgnoreCase(participantUserName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Thành viên không tồn tại trong cuộc họp."));

        if (target.isChairperson()) {
            long hostCount = personals.stream().filter(MeetingPersonal::isChairperson).count();
            if (hostCount <= 1) {
                throw new IllegalStateException("Không thể xóa người chủ trì duy nhất của cuộc họp.");
            }
        }

        meetingPersonalRepository.deleteByMeetingIdAndUserName(meetingId, target.getUserName());
        saveAuditLog(meetingId, "REMOVE_PARTICIPANT", userName, meeting.getVersion(), "Xóa thành viên: " + participantUserName);
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

        boolean isHost = personal.isChairperson();
        boolean isGuest = personal.getType() == 3 || userName.startsWith("guest_");

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
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền bắt đầu cuộc họp này.");
        }

        if (meeting.getStatus() == 3 || meeting.getStatus() == 4) {
            throw new IllegalStateException("Không thể bắt đầu cuộc họp đã kết thúc hoặc đã bị hủy.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setStatus(2);
        meeting.setStartDate(now);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "START", userName, meeting.getVersion() + 1, "Bắt đầu cuộc họp.");
    }

    @Override
    @Transactional
    public void endMeeting(String userName, String meetingId) {
        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));

        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meetingId);
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());
        if (!isHost) {
            throw new SecurityException("Bạn không có quyền kết thúc cuộc họp này.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        meeting.setStatus(3);
        meeting.setEndDate(now);
        meeting.setUpdateBy(userName);
        meeting.setUpdateDate(now);
        meetingInfoRepository.save(meeting);

        saveAuditLog(meetingId, "END", userName, meeting.getVersion() + 1, "Kết thúc cuộc họp.");
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

        List<MeetingMessage> messages = meetingMessageRepository.findByMeetingIdOrderByCreateDateAsc(meetingId);
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

        LocalDateTime now = LocalDateTime.now(clock);
        String messageId = UUID.randomUUID().toString().replace("-", "");

        MeetingMessage msg = new MeetingMessage();
        msg.setId(messageId);
        msg.setMeetingId(meetingId);
        msg.setSenderUserId(userName);
        msg.setReceiverUserId(request.receiverUserId() != null ? request.receiverUserId().trim() : "");
        msg.setMessageText(request.messageText().trim());
        msg.setCreateBy(userName);
        msg.setCreateDate(now);
        msg.setUpdateBy(userName);
        msg.setUpdateDate(now);

        meetingMessageRepository.save(msg);

        return new MeetingMessageResponse(
                msg.getId(),
                msg.getMeetingId(),
                msg.getSenderUserId(),
                personal.getFullName(),
                msg.getReceiverUserId(),
                msg.getMessageText(),
                msg.getCreateDate()
        );
    }

    private MeetingListItemResponse toListItemResponse(MeetingInfo meeting, String userName) {
        List<MeetingPersonal> personals = meetingPersonalRepository.findByMeetingId(meeting.getId());
        MeetingPersonal host = personals.stream()
                .filter(MeetingPersonal::isChairperson)
                .findFirst()
                .orElse(null);
        String hostName = host != null ? host.getFullName() : (meeting.getCreateBy() != null ? meeting.getCreateBy() : "");
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());

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
                .findFirst()
                .orElse(null);
        String hostName = host != null ? host.getFullName() : "";
        boolean isHost = personals.stream()
                .anyMatch(p -> p.getUserName().equalsIgnoreCase(userName) && p.isChairperson());

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
                isHost
        );
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
        if (json == null || json.isBlank()) {
            return MeetingSettingsDto.defaultSettings();
        }
        try {
            return objectMapper.readValue(json, MeetingSettingsDto.class);
        } catch (JsonProcessingException e) {
            return MeetingSettingsDto.defaultSettings();
        }
    }
}
