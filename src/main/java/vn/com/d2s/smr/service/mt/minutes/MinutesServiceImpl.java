package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.config.AdminAccess;
import vn.com.d2s.smr.config.BusinessTime;
import vn.com.d2s.smr.config.LlmProperties;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSettingsDto;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.dto.mt.minutes.MinutesDetailResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesJobResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesListItemResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesVersionResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.entity.mt.MtMinutes;
import vn.com.d2s.smr.entity.sy.SyBackgroundJob;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTranscriptSegmentRepository;
import vn.com.d2s.smr.repository.mt.MtMinutesRepository;
import vn.com.d2s.smr.repository.sy.SyBackgroundJobRepository;
import vn.com.d2s.smr.service.ad.PermissionService;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;
import vn.com.d2s.smr.service.mt.impl.MeetingTranscriptServiceImpl;
import vn.com.d2s.smr.service.mt.impl.RoomAdmission;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;
import vn.com.d2s.smr.service.sy.SettingService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MinutesServiceImpl implements MinutesService {

    private static final Logger log = LoggerFactory.getLogger(MinutesServiceImpl.class);
    static final String JOB_TYPE = "MINUTES_GENERATE";
    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final MeetingTranscriptSegmentRepository segmentRepository;
    private final MtMinutesRepository minutesRepository;
    private final SyBackgroundJobRepository jobRepository;
    private final MeetingTaskRepository taskRepository;
    private final AdAccountRepository accountRepository;
    private final LlmClientService llm;
    private final LlmProperties llmProperties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minutes-ai");
        t.setDaemon(true);
        return t;
    });

    private ApplicationEventPublisher events = e -> { };
    private RoomEventPublisher room = RoomEventPublisher.NO_OP;
    private AdminAccess adminAccess;
    private PermissionService permissionService;
    private SettingService settingService;
    private ObjectStorageService storage;
    /** Unit test chạy job đồng bộ. */
    boolean runJobsInline = false;

    @Autowired
    public MinutesServiceImpl(MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                              MeetingTranscriptSegmentRepository segmentRepository, MtMinutesRepository minutesRepository,
                              SyBackgroundJobRepository jobRepository, MeetingTaskRepository taskRepository,
                              AdAccountRepository accountRepository, LlmClientService llm, LlmProperties llmProperties,
                              ObjectMapper mapper) {
        this(meetingRepository, personalRepository, segmentRepository, minutesRepository, jobRepository, taskRepository,
                accountRepository, llm, llmProperties, mapper, Clock.systemUTC());
    }

    MinutesServiceImpl(MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                       MeetingTranscriptSegmentRepository segmentRepository, MtMinutesRepository minutesRepository,
                       SyBackgroundJobRepository jobRepository, MeetingTaskRepository taskRepository,
                       AdAccountRepository accountRepository, LlmClientService llm, LlmProperties llmProperties,
                       ObjectMapper mapper, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.segmentRepository = segmentRepository;
        this.minutesRepository = minutesRepository;
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
        this.llm = llm;
        this.llmProperties = llmProperties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setEvents(ApplicationEventPublisher events) { this.events = events; }

    @Autowired(required = false)
    void setRoom(RoomEventPublisher room) { this.room = room; }

    @Autowired(required = false)
    void setAdminAccess(AdminAccess adminAccess) { this.adminAccess = adminAccess; }

    @Autowired(required = false)
    void setPermissionService(PermissionService permissionService) { this.permissionService = permissionService; }

    @Autowired(required = false)
    void setSettingService(SettingService settingService) { this.settingService = settingService; }

    @Autowired(required = false)
    void setStorage(ObjectStorageService storage) { this.storage = storage; }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ===================== Vai trò =====================

    private record Ctx(MeetingInfo meeting, List<MeetingPersonal> people, MeetingPersonal me) {
        boolean moderator() { return RoomAdmission.isModerator(me); }
        boolean secretary() { return me.getType() == 3 && !RoomAdmission.isGuest(me.getUserName()); }
        boolean editor() { return moderator() || secretary(); }
    }

    private Ctx ctx(String userName, String meetingId) {
        MeetingInfo m = meetingRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));
        List<MeetingPersonal> people = personalRepository.findByMeetingId(meetingId);
        MeetingPersonal me = people.stream().filter(p -> p.getUserName().equalsIgnoreCase(userName)).findFirst()
                .orElseThrow(() -> new SecurityException("Bạn không phải thành viên cuộc họp này."));
        return new Ctx(m, people, me);
    }

    private Ctx editor(String userName, String meetingId) {
        Ctx c = ctx(userName, meetingId);
        if (!c.editor()) throw new SecurityException("Chỉ chủ trì hoặc thư ký được soạn biên bản.");
        return c;
    }

    private Ctx moderator(String userName, String meetingId) {
        Ctx c = ctx(userName, meetingId);
        if (!c.moderator()) throw new SecurityException("Chỉ chủ trì được duyệt / phát hành biên bản.");
        return c;
    }

    private boolean canUseAi(String userName) {
        if (adminAccess != null && adminAccess.isAdmin(userName)) return true;
        if (permissionService == null) return true;
        try {
            return permissionService.getEffectivePermissions(userName).stream()
                    .anyMatch(p -> "AI_SUMMARY".equalsIgnoreCase(p.code()) && p.allowed());
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ===================== Danh sách =====================

    @Override
    @Transactional(readOnly = true)
    public PagedResultResponse<MinutesListItemResponse> list(String userName, String keyword, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.clamp(pageSize, 1, 100);
        // Đang diễn ra (đã có transcript dần) + đã kết thúc + đã lưu trữ.
        Page<MeetingInfo> meetings = meetingRepository.searchMeetings(userName, false, List.of(2, 3, 5),
                keyword == null ? "" : keyword.trim(), null, null,
                PageRequest.of(p - 1, size, Sort.by(Sort.Direction.DESC, "expectedStartTime")));
        List<String> ids = meetings.getContent().stream().map(MeetingInfo::getId).toList();
        Map<String, Long> counts = ids.isEmpty() ? Map.of() : segmentRepository.countByMeetingIds(ids).stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        Map<String, MtMinutes> latest = ids.isEmpty() ? Map.of() : minutesRepository.findByMeetingIdIn(ids).stream()
                .collect(Collectors.toMap(MtMinutes::getMeetingId, Function.identity(),
                        (a, b) -> a.getVersion() >= b.getVersion() ? a : b));
        List<MinutesListItemResponse> items = meetings.getContent().stream().map(m -> {
            MtMinutes mm = latest.get(m.getId());
            String host = personalRepository.findByMeetingId(m.getId()).stream()
                    .filter(MeetingPersonal::isChairperson).map(MeetingPersonal::getFullName).findFirst().orElse("");
            return new MinutesListItemResponse(m.getId(), m.getName(), m.getRoomCode(), m.getStatus(),
                    m.getStartDate() != null ? m.getStartDate() : m.getExpectedStartTime(),
                    m.getEndDate() != null ? m.getEndDate() : m.getExpectedEndTime(), host,
                    counts.getOrDefault(m.getId(), 0L).intValue(),
                    mm == null ? null : mm.getVersion(), mm == null ? null : mm.getStatus(),
                    publishedAt(m.getId()));
        }).toList();
        return PagedResultResponse.of(items, p, size, meetings.getTotalElements());
    }

    private LocalDateTime publishedAt(String meetingId) {
        return minutesRepository.findFirstByMeetingIdAndStatusOrderByVersionDesc(meetingId, MtMinutes.PUBLISHED)
                .map(MtMinutes::getPublishedAt).orElse(null);
    }

    // ===================== Chi tiết =====================

    @Override
    @Transactional(readOnly = true)
    public MinutesDetailResponse detail(String userName, String meetingId, Integer version) {
        Ctx c = ctx(userName, meetingId);
        List<MtMinutes> all = minutesRepository.findByMeetingIdOrderByVersionDesc(meetingId);
        // Thành viên thường chỉ thấy bản đã phát hành.
        List<MtMinutes> visible = c.editor() ? all : all.stream().filter(m -> m.getStatus() == MtMinutes.PUBLISHED).toList();
        MtMinutes current = version == null ? visible.stream().findFirst().orElse(null)
                : visible.stream().filter(m -> m.getVersion() == version).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên bản biên bản."));
        MinutesJobResponse job = c.editor()
                ? jobRepository.findFirstByRefIdAndTypeOrderByCreatedAtDesc(meetingId, JOB_TYPE).map(j -> toJob(j, false)).orElse(null)
                : null;
        int transcriptCount = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(meetingId).size();
        return new MinutesDetailResponse(meetingId, c.meeting().getName(), c.meeting().getStatus(),
                c.meeting().getStartDate() != null ? c.meeting().getStartDate() : c.meeting().getExpectedStartTime(),
                c.meeting().getEndDate(),
                c.moderator(), c.secretary(), c.editor() && canUseAi(userName), c.editor(), c.moderator(),
                current == null ? null : toVersion(current),
                visible.stream().map(m -> new MinutesDetailResponse.VersionItem(m.getVersion(), m.getStatus(), m.getSource(), m.getCreatedAt())).toList(),
                job,
                c.people().stream().filter(p -> !RoomAdmission.isGuest(p.getUserName()))
                        .map(p -> new MinutesDetailResponse.Person(p.getUserName(), p.getFullName())).toList(),
                transcriptCount);
    }

    // ===================== Sinh bằng AI =====================

    @Override
    public MinutesJobResponse generate(String userName, String meetingId) {
        Ctx c = editor(userName, meetingId);
        if (!canUseAi(userName)) throw new SecurityException("Bạn chưa được cấp quyền dùng AI tóm tắt (AI_SUMMARY).");
        if (c.meeting().getStatus() != 2 && c.meeting().getStatus() != 3 && c.meeting().getStatus() != 5) {
            throw new IllegalStateException("Cuộc họp chưa diễn ra nên chưa có transcript.");
        }
        List<MeetingTranscriptSegment> segs = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(meetingId);
        if (segs.isEmpty()) throw new IllegalStateException("Cuộc họp chưa có transcript. Hãy bật phụ đề trong phòng họp.");

        String key = "MINUTES:" + meetingId + ":" + transcriptHash(segs);
        Optional<SyBackgroundJob> existing = jobRepository.findByIdempotencyKey(key);
        if (existing.isPresent() && existing.get().getStatus() != SyBackgroundJob.FAILED) {
            return toJob(existing.get(), true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        SyBackgroundJob job = existing.orElseGet(() -> {
            SyBackgroundJob j = new SyBackgroundJob();
            j.setId(UUID.randomUUID().toString().replace("-", ""));
            j.setType(JOB_TYPE);
            j.setIdempotencyKey(key);
            j.setRefId(meetingId);
            return j;
        });
        job.setStatus(SyBackgroundJob.PENDING);
        job.setError(null);
        job.setResultRef(null);
        job.setCreatedBy(userName);
        job.setCreatedAt(now);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        try {
            jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException e) {
            // Bấm liên tiếp: request khác vừa tạo job cùng khoá → dùng job đó.
            return jobRepository.findByIdempotencyKey(key).map(j -> toJob(j, true)).orElseThrow(() -> e);
        }
        String jobId = job.getId();
        if (runJobsInline) runJob(jobId); else executor.submit(() -> runJob(jobId));
        return toJob(job, false);
    }

    void runJob(String jobId) {
        SyBackgroundJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus(SyBackgroundJob.RUNNING);
        job.setStartedAt(LocalDateTime.now(clock));
        jobRepository.save(job);
        notifyStatus(job.getRefId(), "running", null);
        try {
            MeetingInfo meeting = meetingRepository.findById(job.getRefId()).orElseThrow();
            List<MeetingPersonal> people = personalRepository.findByMeetingId(meeting.getId());
            List<MeetingTranscriptSegment> segs = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(meeting.getId());
            List<MinutesAiGenerator.Participant> participants = people.stream()
                    .filter(p -> !RoomAdmission.isGuest(p.getUserName()))
                    .map(p -> new MinutesAiGenerator.Participant(p.getUserName(), p.getFullName())).toList();
            MinutesAiGenerator.Prompt prompt = MinutesAiGenerator.build(meeting.getName(),
                    meeting.getStartDate() != null ? meeting.getStartDate() : meeting.getExpectedStartTime(), participants, segs);
            String raw = llm.generate(prompt.system(), prompt.user(), 0.2, 3000);
            if (raw == null || raw.isBlank()) throw new IllegalStateException("Không kết nối được máy chủ AI. Hãy thử lại sau.");
            MinutesContent content = MinutesAiGenerator.parse(raw, prompt.codeToSegment(), participants, mapper);

            MtMinutes m = newVersion(meeting.getId(), job.getCreatedBy());
            m.setContentJson(write(content));
            m.setSource("AI");
            m.setModel(llmProperties.model());
            m.setPromptVersion(MinutesAiGenerator.PROMPT_VERSION);
            m.setTranscriptHash(job.getIdempotencyKey().substring(job.getIdempotencyKey().lastIndexOf(':') + 1));
            minutesRepository.save(m);

            job.setStatus(SyBackgroundJob.DONE);
            job.setResultRef(m.getId());
            job.setFinishedAt(LocalDateTime.now(clock));
            jobRepository.save(job);

            List<String> editors = people.stream()
                    .filter(p -> RoomAdmission.isModerator(p) || p.getType() == 3)
                    .map(MeetingPersonal::getUserName).toList();
            // actor null ("Hệ thống") để cả người bấm sinh cũng nhận thông báo khi rời trang.
            events.publishEvent(new NotificationEvents.MinutesReady(meeting.getId(), null, m.getVersion(), editors));
            notifyStatus(meeting.getId(), "ready", m.getVersion());
        } catch (Exception e) {
            log.warn("Sinh biên bản thất bại (job {}): {}", jobId, e.getMessage());
            job.setStatus(SyBackgroundJob.FAILED);
            job.setError(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 2000));
            job.setFinishedAt(LocalDateTime.now(clock));
            jobRepository.save(job);
            notifyStatus(job.getRefId(), "failed", null);
        }
    }

    private void notifyStatus(String meetingId, String state, Integer version) {
        try {
            List<String> members = personalRepository.findByMeetingId(meetingId).stream().map(MeetingPersonal::getUserName).toList();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("meetingId", meetingId);
            payload.put("state", state);
            if (version != null) payload.put("version", version);
            room.sendToUsers(members, "MinutesStatusChanged", payload);
        } catch (RuntimeException ignored) {
            // realtime lỗi không ảnh hưởng nghiệp vụ
        }
    }

    static String transcriptHash(List<MeetingTranscriptSegment> segs) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (MeetingTranscriptSegment s : segs) {
                md.update(s.getId().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(Objects.toString(s.getSpeakerName(), "").getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(s.getText().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ===================== Soạn / duyệt / phát hành =====================

    @Override
    @Transactional
    public MinutesVersionResponse newDraft(String userName, String meetingId) {
        editor(userName, meetingId);
        MinutesContent base = minutesRepository.findFirstByMeetingIdOrderByVersionDesc(meetingId)
                .map(m -> read(m.getContentJson())).orElse(MinutesContent.empty());
        // Chép nội dung nhưng bỏ liên kết công việc đã tạo, để phát hành lại không tạo trùng.
        MtMinutes m = newVersion(meetingId, userName);
        m.setContentJson(write(base));
        m.setSource("MANUAL");
        minutesRepository.save(m);
        return toVersion(m);
    }

    @Override
    @Transactional
    public MinutesVersionResponse update(String userName, String meetingId, int version, MinutesContent content) {
        editor(userName, meetingId);
        MtMinutes m = find(meetingId, version);
        if (m.getStatus() != MtMinutes.DRAFT && m.getStatus() != MtMinutes.REJECTED) {
            throw new IllegalStateException("Chỉ sửa được bản nháp hoặc bản bị từ chối. Bản đã gửi duyệt / phát hành không sửa được.");
        }
        MinutesContent clean = sanitize(content, meetingId);
        m.setContentJson(write(clean));
        m.setStatus(MtMinutes.DRAFT);
        m.setRejectReason(null);
        touch(m, userName);
        minutesRepository.save(m);
        return toVersion(m);
    }

    @Override
    @Transactional
    public MinutesVersionResponse submit(String userName, String meetingId, int version) {
        editor(userName, meetingId);
        MtMinutes m = find(meetingId, version);
        requireStatus(m, MtMinutes.DRAFT, "Chỉ gửi duyệt được bản nháp.");
        m.setStatus(MtMinutes.SUBMITTED);
        m.setSubmittedBy(userName);
        m.setSubmittedAt(LocalDateTime.now(clock));
        minutesRepository.save(m);
        notifyStatus(meetingId, "submitted", version);
        return toVersion(m);
    }

    @Override
    @Transactional
    public MinutesVersionResponse approve(String userName, String meetingId, int version) {
        moderator(userName, meetingId);
        MtMinutes m = find(meetingId, version);
        requireStatus(m, MtMinutes.SUBMITTED, "Chỉ duyệt được bản đang chờ duyệt.");
        m.setStatus(MtMinutes.APPROVED);
        m.setApprovedBy(userName);
        m.setApprovedAt(LocalDateTime.now(clock));
        minutesRepository.save(m);
        notifyStatus(meetingId, "approved", version);
        return toVersion(m);
    }

    @Override
    @Transactional
    public MinutesVersionResponse reject(String userName, String meetingId, int version, String reason) {
        moderator(userName, meetingId);
        MtMinutes m = find(meetingId, version);
        requireStatus(m, MtMinutes.SUBMITTED, "Chỉ từ chối được bản đang chờ duyệt.");
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập lý do từ chối.");
        m.setStatus(MtMinutes.REJECTED);
        m.setRejectReason(truncate(r, 1000));
        touch(m, userName);
        minutesRepository.save(m);
        notifyStatus(meetingId, "rejected", version);
        return toVersion(m);
    }

    @Override
    @Transactional
    public MinutesVersionResponse publish(String userName, String meetingId, int version) {
        Ctx c = moderator(userName, meetingId);
        MtMinutes m = find(meetingId, version);
        requireStatus(m, MtMinutes.APPROVED, "Chỉ phát hành được bản đã duyệt.");
        MinutesContent content = read(m.getContentJson());
        List<String> warnings = MinutesAiGenerator.warnings(content);
        if (!warnings.isEmpty()) {
            throw new IllegalStateException("Chưa phát hành được: " + String.join("; ", warnings) + ".");
        }
        MinutesContent withTasks = createTasks(c, m, content, userName);
        LocalDateTime now = LocalDateTime.now(clock);
        m.setContentJson(write(withTasks));
        m.setStatus(MtMinutes.PUBLISHED);
        m.setPublishedBy(userName);
        m.setPublishedAt(now);
        minutesRepository.save(m);
        events.publishEvent(new NotificationEvents.MinutesPublished(meetingId, userName, version));
        notifyStatus(meetingId, "published", version);
        return toVersion(m);
    }

    /** Việc cần làm đã xác nhận → việc nhỏ trên bảng "Việc từ biên bản" của cuộc họp (tạo bảng nếu chưa có). */
    private MinutesContent createTasks(Ctx c, MtMinutes m, MinutesContent content, String userName) {
        List<MinutesContent.ActionItem> todo = content.actions().stream().filter(a -> a.confirmed() && a.taskId() == null).toList();
        if (todo.isEmpty()) return content;
        LocalDateTime now = LocalDateTime.now(clock);
        String boardRef = "minutes-board:" + c.meeting().getId();
        MeetingTask board = taskRepository.findByMeetingId(c.meeting().getId()).stream()
                .filter(t -> t.getLevel() == 0 && boardRef.equals(t.getSourceRef())).findFirst()
                .orElseGet(() -> taskRepository.save(newTask(null, 0, truncate("Việc từ biên bản: " + c.meeting().getName(), 300),
                        c.meeting().getId(), userName, now, boardRef, null, null, 0)));
        if (board.getColor() == null) {
            board.setColor("#519839");
            taskRepository.save(board);
        }
        MeetingTask list = taskRepository.findByParentId(board.getId()).stream()
                .min(Comparator.comparingInt(MeetingTask::getSortOrder))
                .orElseGet(() -> taskRepository.save(newTask(board.getId(), 1, "Việc cần làm", c.meeting().getId(), userName, now, null, null, null, 0)));
        int order = taskRepository.maxSortOrder(list.getId()) + 1;

        List<MinutesContent.ActionItem> result = new ArrayList<>();
        for (MinutesContent.ActionItem a : content.actions()) {
            if (!a.confirmed() || a.taskId() != null) {
                result.add(a);
                continue;
            }
            String assignee = a.assignee() == null ? null : accountRepository.findById(a.assignee())
                    .filter(AdAccount::isActive).map(AdAccount::getUserName).orElse(null);
            LocalDateTime due = a.dueDate() == null ? null
                    : LocalDate.parse(a.dueDate()).atTime(LocalTime.of(17, 0)).atZone(BusinessTime.ZONE)
                    .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            MeetingTask card = taskRepository.save(newTask(list.getId(), 2, truncate(a.text(), 300), c.meeting().getId(), userName, now,
                    "minutes:" + m.getId() + ":" + a.id(), assignee, due, order++));
            if (assignee != null) {
                events.publishEvent(new NotificationEvents.TaskAssigned(card.getId(), userName, assignee));
            }
            result.add(new MinutesContent.ActionItem(a.id(), a.text(), a.assignee(), a.assigneeName(), a.dueDate(), a.sources(), true, card.getId()));
        }
        return new MinutesContent(content.summary(), content.summarySources(), content.decisions(), result);
    }

    private static MeetingTask newTask(String parentId, int level, String title, String meetingId, String user, LocalDateTime now,
                                       String sourceRef, String assignee, LocalDateTime due, int order) {
        MeetingTask t = new MeetingTask();
        t.setId(UUID.randomUUID().toString().replace("-", ""));
        t.setParentId(parentId);
        t.setLevel(level);
        t.setTitle(title);
        t.setMeetingId(meetingId);
        t.setSourceRef(sourceRef);
        t.setAssigneeUserName(assignee);
        t.setDueDate(due);
        t.setStatus(0);
        t.setPriority(1);
        t.setSortOrder(order);
        t.setCreateBy(user);
        t.setCreateDate(now);
        t.setUpdateBy(user);
        t.setUpdateDate(now);
        return t;
    }

    // ===================== DOCX =====================

    @Override
    @Transactional(readOnly = true)
    public ExportedDocx exportDocx(String userName, String meetingId, int version) {
        Ctx c = ctx(userName, meetingId);
        MtMinutes m = find(meetingId, version);
        if (m.getStatus() != MtMinutes.PUBLISHED && !c.editor()) {
            throw new SecurityException("Chỉ tải được biên bản đã phát hành.");
        }
        MinutesContent content = read(m.getContentJson());
        Map<String, String> values = placeholders(c, content);
        byte[] bytes;
        Optional<String> template = Optional.empty();
        if (settingService != null && storage != null) {
            MeetingSettingsDto s = MeetingSettingsDto.fromJson(c.meeting().getSettingsJson(), mapper);
            template = settingService.templateObjectName(s.minutesTemplateId());
        }
        if (template.isPresent()) {
            try (InputStream in = storage.download(template.get())) {
                bytes = MinutesDocxRenderer.render(in, values);
            } catch (Exception e) {
                log.warn("Không dùng được mẫu biên bản {}: {} — dùng mẫu mặc định", template.get(), e.getMessage());
                bytes = MinutesDocxRenderer.render(null, values);
            }
        } else {
            bytes = MinutesDocxRenderer.render(null, values);
        }
        String date = c.meeting().getStartDate() == null ? "" : "_" + c.meeting().getStartDate().atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(BusinessTime.ZONE).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return new ExportedDocx("BienBan_" + c.meeting().getRoomCode() + date + "_v" + version + ".docx", bytes);
    }

    private Map<String, String> placeholders(Ctx c, MinutesContent content) {
        MeetingInfo meeting = c.meeting();
        LocalDateTime start = meeting.getStartDate() != null ? meeting.getStartDate() : meeting.getExpectedStartTime();
        LocalDateTime end = meeting.getEndDate() != null ? meeting.getEndDate() : meeting.getExpectedEndTime();
        Map<String, MeetingTranscriptSegment> segs = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(meeting.getId()).stream()
                .collect(Collectors.toMap(MeetingTranscriptSegment::getId, Function.identity(), (a, b) -> a));
        StringBuilder decisions = new StringBuilder();
        for (int i = 0; i < content.decisions().size(); i++) {
            var d = content.decisions().get(i);
            decisions.append(i + 1).append(". ").append(d.text()).append(times(d.sources(), segs, start)).append('\n');
        }
        StringBuilder actions = new StringBuilder();
        for (int i = 0; i < content.actions().size(); i++) {
            var a = content.actions().get(i);
            actions.append(i + 1).append(". ").append(a.text())
                    .append(" — Phụ trách: ").append(a.assigneeName() != null ? a.assigneeName() : a.assignee() != null ? a.assignee() : "Chưa xác định")
                    .append(" — Hạn: ").append(a.dueDate() != null ? LocalDate.parse(a.dueDate()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Chưa xác định")
                    .append('\n');
        }
        String participants = c.people().stream()
                .map(p -> "- " + p.getFullName() + (p.getType() == 1 ? " (Chủ trì)" : RoomAdmission.isModerator(p) ? " (Đồng chủ trì)" : p.getType() == 3 ? " (Thư ký)" : ""))
                .collect(Collectors.joining("\n"));
        return Map.of(
                "{{meeting_name}}", meeting.getName(),
                "{{time}}", (start == null ? "" : vn(start)) + (end == null ? "" : " – " + vn(end)),
                "{{participants}}", participants,
                "{{summary}}", content.summary(),
                "{{decisions}}", decisions.isEmpty() ? "Không có" : decisions.toString().trim(),
                "{{action_items}}", actions.isEmpty() ? "Không có" : actions.toString().trim());
    }

    private static String times(List<String> sources, Map<String, MeetingTranscriptSegment> segs, LocalDateTime start) {
        List<String> t = sources.stream().map(segs::get).filter(Objects::nonNull)
                .map(s -> MinutesAiGenerator.offset(start, s.getSpokenAt())).toList();
        return t.isEmpty() ? "" : " (⏱ " + String.join(", ", t) + ")";
    }

    private static String vn(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE).format(VN_TIME);
    }

    // ===================== Transcript =====================

    @Override
    @Transactional
    public MeetingCaptionDto updateSegment(String userName, String meetingId, String segmentId, String text, String speakerName) {
        editor(userName, meetingId);
        MeetingTranscriptSegment s = segmentRepository.findById(segmentId)
                .filter(x -> x.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đoạn transcript."));
        if (text != null) {
            String t = text.trim();
            if (t.isEmpty()) throw new IllegalArgumentException("Nội dung đoạn không được để trống.");
            s.setText(t);
        }
        if (speakerName != null && !speakerName.isBlank()) s.setSpeakerName(truncate(speakerName.trim(), 200));
        s.setEditedBy(userName);
        s.setEditedAt(LocalDateTime.now(clock));
        segmentRepository.save(s);
        return MeetingTranscriptServiceImpl.toDto(s);
    }

    // ===================== Tiện ích =====================

    private MtMinutes newVersion(String meetingId, String userName) {
        int next = minutesRepository.findFirstByMeetingIdOrderByVersionDesc(meetingId).map(MtMinutes::getVersion).orElse(0) + 1;
        MtMinutes m = new MtMinutes();
        m.setId(UUID.randomUUID().toString().replace("-", ""));
        m.setMeetingId(meetingId);
        m.setVersion(next);
        m.setStatus(MtMinutes.DRAFT);
        m.setCreatedBy(userName);
        m.setCreatedAt(LocalDateTime.now(clock));
        return m;
    }

    private MtMinutes find(String meetingId, int version) {
        return minutesRepository.findByMeetingIdAndVersion(meetingId, version)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên bản biên bản."));
    }

    private static void requireStatus(MtMinutes m, int status, String message) {
        if (m.getStatus() != status) throw new IllegalStateException(message);
    }

    private void touch(MtMinutes m, String userName) {
        m.setUpdatedBy(userName);
        m.setUpdatedAt(LocalDateTime.now(clock));
    }

    /** Bỏ nguồn không thuộc cuộc họp, chuẩn hoá ngày, giữ liên kết công việc cũ. */
    private MinutesContent sanitize(MinutesContent content, String meetingId) {
        if (content == null) return MinutesContent.empty();
        java.util.Set<String> valid = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(meetingId).stream()
                .map(MeetingTranscriptSegment::getId).collect(Collectors.toSet());
        Function<List<String>, List<String>> keep = l -> l.stream().filter(valid::contains).distinct().toList();
        List<MinutesContent.Decision> decisions = content.decisions().stream()
                .filter(d -> d.text() != null && !d.text().isBlank())
                .map(d -> new MinutesContent.Decision(d.id() == null ? MinutesAiGenerator.newId() : d.id(), d.text().trim(), keep.apply(d.sources())))
                .toList();
        Map<String, String> names = personalRepository.findByMeetingId(meetingId).stream()
                .collect(Collectors.toMap(p -> p.getUserName().toLowerCase(Locale.ROOT), MeetingPersonal::getFullName, (a, b) -> a));
        List<MinutesContent.ActionItem> actions = content.actions().stream()
                .filter(a -> a.text() != null && !a.text().isBlank())
                .map(a -> {
                    String assignee = a.assignee() == null || a.assignee().isBlank() ? null : a.assignee().trim();
                    String name = assignee == null ? null : names.getOrDefault(assignee.toLowerCase(Locale.ROOT), a.assigneeName());
                    String due;
                    try {
                        due = a.dueDate() == null || a.dueDate().isBlank() ? null : LocalDate.parse(a.dueDate().substring(0, 10)).toString();
                    } catch (Exception e) {
                        due = null;
                    }
                    return new MinutesContent.ActionItem(a.id() == null ? MinutesAiGenerator.newId() : a.id(), a.text().trim(),
                            assignee, name, due, keep.apply(a.sources()), a.confirmed(), a.taskId());
                }).toList();
        return new MinutesContent(content.summary() == null ? "" : content.summary().trim(), keep.apply(content.summarySources()), decisions, actions);
    }

    private MinutesVersionResponse toVersion(MtMinutes m) {
        MinutesContent c = read(m.getContentJson());
        return new MinutesVersionResponse(m.getId(), m.getVersion(), m.getStatus(), c, m.getSource(), m.getModel(),
                m.getPromptVersion(), m.getRejectReason(), m.getCreatedBy(), m.getCreatedAt(), m.getUpdatedBy(), m.getUpdatedAt(),
                m.getSubmittedBy(), m.getSubmittedAt(), m.getApprovedBy(), m.getApprovedAt(), m.getPublishedBy(), m.getPublishedAt(),
                MinutesAiGenerator.warnings(c));
    }

    private static MinutesJobResponse toJob(SyBackgroundJob j, boolean reused) {
        return new MinutesJobResponse(j.getId(), j.getStatus(), j.getError(), j.getCreatedAt(), j.getFinishedAt(), reused);
    }

    private MinutesContent read(String json) {
        try {
            return json == null || json.isBlank() ? MinutesContent.empty() : mapper.readValue(json, MinutesContent.class);
        } catch (JsonProcessingException e) {
            return MinutesContent.empty();
        }
    }

    private String write(MinutesContent c) {
        try {
            return mapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
