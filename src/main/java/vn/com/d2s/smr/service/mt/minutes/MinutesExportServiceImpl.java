package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.config.BusinessTime;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSettingsDto;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.dto.mt.minutes.MinutesExportResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesTemplateInfo;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.entity.mt.MtMinutes;
import vn.com.d2s.smr.entity.sy.SyBackgroundJob;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTranscriptSegmentRepository;
import vn.com.d2s.smr.repository.mt.MtMinutesRepository;
import vn.com.d2s.smr.repository.sy.SyBackgroundJobRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;
import vn.com.d2s.smr.service.mt.impl.RoomAdmission;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Kind;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Section;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Template;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;
import vn.com.d2s.smr.service.sy.SettingService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class MinutesExportServiceImpl implements MinutesExportService {

    private static final Logger log = LoggerFactory.getLogger(MinutesExportServiceImpl.class);
    static final String JOB_TYPE = "MINUTES_EXPORT";
    /** CmFiles.Type: 6 = mẫu biên bản của cuộc họp · 7 = file biên bản đã dựng. RefrenceFileId = meetingId. */
    public static final int FILE_TYPE_MEETING_TEMPLATE = 6;
    public static final int FILE_TYPE_EXPORT = 7;
    static final long MAX_TEMPLATE_BYTES = 10L * 1024 * 1024;
    static final int MAX_NEW_JOBS_PER_HOUR = 5;
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final MeetingTranscriptSegmentRepository segmentRepository;
    private final MtMinutesRepository minutesRepository;
    private final MeetingTaskRepository taskRepository;
    private final SyBackgroundJobRepository jobRepository;
    private final CmFileRepository fileRepository;
    private final AdAccountRepository accountRepository;
    private final ObjectStorageService storage;
    private final LlmClientService llm;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minutes-export");
        t.setDaemon(true);
        return t;
    });

    private ApplicationEventPublisher events = e -> { };
    private RoomEventPublisher room = RoomEventPublisher.NO_OP;
    private SettingService settingService;
    /** Unit test chạy job đồng bộ. */
    boolean runJobsInline = false;

    @Autowired
    public MinutesExportServiceImpl(MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                                    MeetingTranscriptSegmentRepository segmentRepository, MtMinutesRepository minutesRepository,
                                    MeetingTaskRepository taskRepository, SyBackgroundJobRepository jobRepository,
                                    CmFileRepository fileRepository, AdAccountRepository accountRepository,
                                    ObjectStorageService storage, LlmClientService llm, ObjectMapper mapper) {
        this(meetingRepository, personalRepository, segmentRepository, minutesRepository, taskRepository, jobRepository,
                fileRepository, accountRepository, storage, llm, mapper, Clock.systemUTC());
    }

    MinutesExportServiceImpl(MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                             MeetingTranscriptSegmentRepository segmentRepository, MtMinutesRepository minutesRepository,
                             MeetingTaskRepository taskRepository, SyBackgroundJobRepository jobRepository,
                             CmFileRepository fileRepository, AdAccountRepository accountRepository,
                             ObjectStorageService storage, LlmClientService llm, ObjectMapper mapper, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.segmentRepository = segmentRepository;
        this.minutesRepository = minutesRepository;
        this.taskRepository = taskRepository;
        this.jobRepository = jobRepository;
        this.fileRepository = fileRepository;
        this.accountRepository = accountRepository;
        this.storage = storage;
        this.llm = llm;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setEvents(ApplicationEventPublisher events) { this.events = events; }

    @Autowired(required = false)
    void setRoom(RoomEventPublisher room) { this.room = room; }

    @Autowired(required = false)
    void setSettingService(SettingService settingService) { this.settingService = settingService; }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ===================== Quyền =====================

    private record Ctx(MeetingInfo meeting, List<MeetingPersonal> people, MeetingPersonal me) {
    }

    private Ctx member(String userName, String meetingId) {
        MeetingInfo m = meetingRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));
        if (RoomAdmission.isGuest(userName)) {
            throw new SecurityException("Khách không dùng được chức năng biên bản.");
        }
        List<MeetingPersonal> people = personalRepository.findByMeetingId(meetingId);
        MeetingPersonal me = people.stream().filter(p -> p.getUserName().equalsIgnoreCase(userName)).findFirst()
                .orElseThrow(() -> new SecurityException("Bạn không phải thành viên cuộc họp này."));
        return new Ctx(m, people, me);
    }

    // ===================== Mẫu =====================

    private Optional<CmFile> meetingTemplate(String meetingId) {
        return fileRepository.findByTypeAndReferenceFileIdOrderByCreateDateDesc(FILE_TYPE_MEETING_TEMPLATE, meetingId).stream().findFirst();
    }

    @Override
    public MinutesTemplateInfo uploadTemplate(String userName, String meetingId, MultipartFile file) {
        Ctx c = member(userName, meetingId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file mẫu biên bản.");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw new IllegalArgumentException("Mẫu biên bản không vượt quá 10 MB.");
        }
        String name = cleanName(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file mẫu.");
        }
        // Đọc thử ngay khi tải lên để báo lỗi sớm (file hỏng, PDF scan, không nhận ra mục).
        Template template = MinutesTemplateReader.read(name, bytes);
        boolean docx = template.format() == MinutesTemplateReader.Format.DOCX;

        String id = UUID.randomUUID().toString().replace("-", "");
        String ext = docx ? ".docx" : ".pdf";
        String objectName = "templates/meeting/" + meetingId + "/" + id + ext;
        String mime = docx ? DOCX_MIME : "application/pdf";
        storage.upload(objectName, new ByteArrayInputStream(bytes), bytes.length, mime);

        List<CmFile> old = fileRepository.findByTypeAndReferenceFileIdOrderByCreateDateDesc(FILE_TYPE_MEETING_TEMPLATE, meetingId);
        CmFile f = newFile(id, name, bytes.length, mime, ext, FILE_TYPE_MEETING_TEMPLATE, meetingId, objectName, userName);
        try {
            fileRepository.save(f);
        } catch (RuntimeException e) {
            storage.delete(objectName);
            throw e;
        }
        // Mỗi cuộc họp chỉ một mẫu: mẫu sau thay mẫu trước.
        old.forEach(this::removeFile);
        return toInfo(f, template, c);
    }

    @Override
    public MinutesTemplateInfo template(String userName, String meetingId) {
        Ctx c = member(userName, meetingId);
        Optional<CmFile> own = meetingTemplate(meetingId);
        if (own.isPresent()) {
            Template t = null;
            try {
                t = MinutesTemplateReader.read(own.get().getFileName(), readObject(own.get().getObjectName()));
            } catch (RuntimeException e) {
                log.warn("Không đọc lại được mẫu {} của cuộc họp {}: {}", own.get().getId(), meetingId, e.getMessage());
            }
            return toInfo(own.get(), t, c);
        }
        boolean system = systemTemplateObject(c.meeting()).isPresent();
        return new MinutesTemplateInfo(system ? "SYSTEM" : "BUILTIN", null,
                system ? "Mẫu mặc định của hệ thống" : "Bố cục dựng sẵn", "DOCX", null, null, null, List.of(), false);
    }

    @Override
    public void deleteTemplate(String userName, String meetingId) {
        Ctx c = member(userName, meetingId);
        CmFile f = meetingTemplate(meetingId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mẫu của cuộc họp."));
        if (!f.getCreateBy().equalsIgnoreCase(userName) && !RoomAdmission.isModerator(c.me())) {
            throw new SecurityException("Chỉ người tải mẫu hoặc chủ trì được xóa mẫu.");
        }
        removeFile(f);
    }

    private MinutesTemplateInfo toInfo(CmFile f, Template t, Ctx c) {
        String by = f.getCreateBy();
        String byName = accountRepository.findById(by).map(AdAccount::getFullName).filter(n -> n != null && !n.isBlank()).orElse(by);
        boolean canDelete = by.equalsIgnoreCase(c.me().getUserName()) || RoomAdmission.isModerator(c.me());
        return new MinutesTemplateInfo("MEETING", f.getId(), f.getFileName(),
                ".pdf".equalsIgnoreCase(f.getExtension()) ? "PDF" : "DOCX", by, byName, f.getCreateDate(),
                t == null ? List.of() : t.sectionTitles(), canDelete);
    }

    private Optional<String> systemTemplateObject(MeetingInfo meeting) {
        if (settingService == null) return Optional.empty();
        try {
            MeetingSettingsDto s = MeetingSettingsDto.fromJson(meeting.getSettingsJson(), mapper);
            return settingService.templateObjectName(s.minutesTemplateId());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    // ===================== Yêu cầu tải biên bản =====================

    @Override
    public MinutesExportResponse requestExport(String userName, String meetingId) {
        Ctx c = member(userName, meetingId);
        String key = "MINEXP:" + meetingId + ":" + dataHash(c);
        Optional<SyBackgroundJob> existing = jobRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            SyBackgroundJob j = existing.get();
            boolean running = j.getStatus() == SyBackgroundJob.PENDING || j.getStatus() == SyBackgroundJob.RUNNING;
            boolean doneWithFile = j.getStatus() == SyBackgroundJob.DONE && j.getResultRef() != null
                    && fileRepository.findById(j.getResultRef()).isPresent();
            // Đang chạy → gắn vào job đó; đã xong và dữ liệu chưa đổi → dùng lại file.
            if (running || doneWithFile) return toResponse(j, c.meeting(), true);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (jobRepository.countByTypeAndRefIdAndCreatedByAndCreatedAtAfter(JOB_TYPE, meetingId, userName, now.minusHours(1)) >= MAX_NEW_JOBS_PER_HOUR) {
            throw new IllegalStateException("Bạn đã yêu cầu tổng hợp biên bản " + MAX_NEW_JOBS_PER_HOUR + " lần trong 1 giờ qua. Vui lòng thử lại sau.");
        }
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
            return jobRepository.findByIdempotencyKey(key).map(j -> toResponse(j, c.meeting(), true)).orElseThrow(() -> e);
        }
        String jobId = job.getId();
        if (runJobsInline) runJob(jobId); else executor.submit(() -> runJob(jobId));
        return toResponse(job, c.meeting(), false);
    }

    @Override
    public MinutesExportResponse status(String userName, String exportId) {
        SyBackgroundJob j = job(exportId);
        Ctx c = member(userName, j.getRefId());
        return toResponse(j, c.meeting(), false);
    }

    @Override
    public MinutesService.ExportedDocx download(String userName, String exportId) {
        SyBackgroundJob j = job(exportId);
        member(userName, j.getRefId());
        if (j.getStatus() != SyBackgroundJob.DONE || j.getResultRef() == null) {
            throw new IllegalStateException("Biên bản chưa tổng hợp xong.");
        }
        CmFile f = fileRepository.findById(j.getResultRef())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy file biên bản. Hãy bấm tải lại để tổng hợp lại."));
        return new MinutesService.ExportedDocx(f.getFileName(), readObject(f.getObjectName()));
    }

    private SyBackgroundJob job(String exportId) {
        return jobRepository.findById(exportId).filter(j -> JOB_TYPE.equals(j.getType()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu tải biên bản."));
    }

    private static MinutesExportResponse toResponse(SyBackgroundJob j, MeetingInfo meeting, boolean reused) {
        return new MinutesExportResponse(j.getId(), meeting.getId(), meeting.getName(), j.getStatus(), j.getError(), reused);
    }

    // ===================== Job nền =====================

    void runJob(String jobId) {
        SyBackgroundJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus(SyBackgroundJob.RUNNING);
        job.setStartedAt(LocalDateTime.now(clock));
        jobRepository.save(job);
        String meetingId = job.getRefId();
        boolean ok = false;
        try {
            MeetingInfo meeting = meetingRepository.findById(meetingId).orElseThrow();
            List<MeetingPersonal> people = personalRepository.findByMeetingId(meetingId);
            byte[] docx = build(meeting, people);

            String fileId = UUID.randomUUID().toString().replace("-", "");
            String objectName = "exports/minutes/" + meetingId + "/" + fileId + ".docx";
            storage.upload(objectName, new ByteArrayInputStream(docx), docx.length, DOCX_MIME);
            LocalDateTime start = startOf(meeting);
            String date = start == null ? "" : "_" + start.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            fileRepository.save(newFile(fileId, "BienBan_" + meeting.getRoomCode() + date + ".docx", docx.length, DOCX_MIME, ".docx",
                    FILE_TYPE_EXPORT, meetingId, objectName, job.getCreatedBy()));

            job.setStatus(SyBackgroundJob.DONE);
            job.setResultRef(fileId);
            ok = true;
        } catch (Exception e) {
            log.warn("Tổng hợp biên bản thất bại (job {}): {}", jobId, e.getMessage());
            job.setStatus(SyBackgroundJob.FAILED);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.setError(msg.length() > 2000 ? msg.substring(0, 2000) : msg);
        }
        job.setFinishedAt(LocalDateTime.now(clock));
        jobRepository.save(job);
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("exportId", jobId);
            payload.put("meetingId", meetingId);
            payload.put("status", job.getStatus());
            room.sendToUsers(personalRepository.findByMeetingId(meetingId).stream().map(MeetingPersonal::getUserName).toList(),
                    "MinutesExportReady", payload);
        } catch (RuntimeException ignored) {
            // realtime lỗi → FE vẫn hỏi lại định kỳ
        }
        events.publishEvent(new NotificationEvents.MinutesExportReady(meetingId, job.getCreatedBy(), jobId, ok));
    }

    /** Dựng file theo mẫu đang dùng. */
    byte[] build(MeetingInfo meeting, List<MeetingPersonal> people) throws IOException {
        List<MeetingTranscriptSegment> segs = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(meeting.getId());
        MtMinutes publishedRow = minutesRepository.findFirstByMeetingIdAndStatusOrderByVersionDesc(meeting.getId(), MtMinutes.PUBLISHED).orElse(null);
        MinutesContent published = publishedRow == null ? null : readContent(publishedRow.getContentJson());
        String note = publishedRow != null
                ? "Quyết định và việc cần làm lấy theo biên bản đã phát hành (phiên bản " + publishedRow.getVersion() + "). Các mục khác do AI tổng hợp."
                : "Biên bản do AI tổng hợp, chưa qua duyệt.";

        Optional<CmFile> own = meetingTemplate(meeting.getId());
        if (own.isPresent()) {
            byte[] bytes = readObject(own.get().getObjectName());
            Template template = MinutesTemplateReader.read(own.get().getFileName(), bytes);
            if (!template.placeholderBased()) {
                Map<String, String> content = sectionContent(template, meeting, people, segs, published);
                return template.format() == MinutesTemplateReader.Format.DOCX
                        ? MinutesTemplateRenderer.fillDocx(bytes, template, content, note)
                        : MinutesTemplateRenderer.buildNew(template, "BIÊN BẢN CUỘC HỌP", content, note);
            }
            return MinutesDocxRenderer.render(new ByteArrayInputStream(bytes), placeholderValues(meeting, people, segs, published, note));
        }
        Map<String, String> values = placeholderValues(meeting, people, segs, published, note);
        Optional<String> system = systemTemplateObject(meeting);
        if (system.isPresent()) {
            try (InputStream in = storage.download(system.get())) {
                return MinutesDocxRenderer.render(in, values);
            } catch (Exception e) {
                log.warn("Không dùng được mẫu hệ thống {}: {} — dùng bố cục dựng sẵn", system.get(), e.getMessage());
            }
        }
        return MinutesDocxRenderer.render(null, values);
    }

    /** Mẫu tự do: mục cố định lấy từ dữ liệu, mục còn lại AI viết. */
    private Map<String, String> sectionContent(Template template, MeetingInfo meeting, List<MeetingPersonal> people,
                                               List<MeetingTranscriptSegment> segs, MinutesContent published) {
        Map<String, String> out = new LinkedHashMap<>();
        List<Section> ai = new java.util.ArrayList<>();
        for (Section s : template.sections()) {
            String fixed = switch (s.kind()) {
                case MEETING_NAME -> meeting.getName();
                case TIME -> timeText(meeting);
                case PLACE -> "Họp trực tuyến trên hệ thống Smart Meeting (mã phòng " + meeting.getRoomCode() + ")";
                case HOST -> names(people, 1);
                case SECRETARY -> names(people, 3);
                case PARTICIPANTS -> participantsText(people, s.inline());
                case DECISIONS -> published == null ? null : decisionsText(published);
                case ACTIONS -> published == null ? null : actionsText(published);
                case AI -> null;
            };
            if (fixed == null && (s.kind() == Kind.AI || s.kind() == Kind.DECISIONS || s.kind() == Kind.ACTIONS)) {
                ai.add(s);
            } else {
                out.put(s.id(), fixed == null || fixed.isBlank() ? MinutesTemplateRenderer.NO_INFO : fixed);
            }
        }
        if (!ai.isEmpty()) {
            MinutesTemplateGenerator.Prompt prompt = MinutesTemplateGenerator.build(ai, knowledge(meeting, people, segs, published));
            String raw = llm.generate(prompt.system(), prompt.user(), 0.2, 3500);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("Không kết nối được máy chủ AI. Hãy thử lại sau.");
            }
            out.putAll(MinutesTemplateGenerator.parse(raw, ai, mapper));
        }
        return out;
    }

    /** Mẫu dùng placeholder (P7) hoặc bố cục dựng sẵn. */
    private Map<String, String> placeholderValues(MeetingInfo meeting, List<MeetingPersonal> people, List<MeetingTranscriptSegment> segs,
                                                  MinutesContent published, String note) {
        MinutesContent content = published;
        if (content == null) {
            if (segs.isEmpty()) {
                content = new MinutesContent(MinutesTemplateRenderer.NO_INFO + " (cuộc họp chưa có transcript).", List.of(), List.of(), List.of());
            } else {
                List<MinutesAiGenerator.Participant> participants = people.stream().filter(p -> !RoomAdmission.isGuest(p.getUserName()))
                        .map(p -> new MinutesAiGenerator.Participant(p.getUserName(), p.getFullName())).toList();
                MinutesAiGenerator.Prompt prompt = MinutesAiGenerator.build(meeting.getName(), startOf(meeting), participants, segs);
                String raw = llm.generate(prompt.system(), prompt.user(), 0.2, 3000);
                if (raw == null || raw.isBlank()) {
                    throw new IllegalStateException("Không kết nối được máy chủ AI. Hãy thử lại sau.");
                }
                content = MinutesAiGenerator.parse(raw, prompt.codeToSegment(), participants, mapper);
            }
        }
        Map<String, String> values = new HashMap<>();
        values.put("{{meeting_name}}", meeting.getName());
        values.put("{{time}}", timeText(meeting));
        values.put("{{participants}}", participantsText(people, false));
        values.put("{{summary}}", (content.summary().isBlank() ? MinutesTemplateRenderer.NO_INFO : content.summary()) + "\n\n" + note);
        values.put("{{decisions}}", decisionsText(content));
        values.put("{{action_items}}", actionsText(content));
        return values;
    }

    // ===================== Kiến thức cuộc họp =====================

    private String knowledge(MeetingInfo meeting, List<MeetingPersonal> people, List<MeetingTranscriptSegment> segs, MinutesContent published) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Thông tin cuộc họp\n");
        sb.append("Tên: ").append(meeting.getName()).append('\n');
        sb.append("Thời gian: ").append(timeText(meeting)).append('\n');
        if (notBlank(meeting.getMeetContent())) sb.append("Mục tiêu / nội dung: ").append(meeting.getMeetContent()).append('\n');
        if (notBlank(meeting.getAgenda())) sb.append("Chương trình họp: ").append(meeting.getAgenda()).append('\n');
        sb.append("\n## Thành phần\n").append(participantsText(people, false)).append('\n');

        List<MeetingTask> tasks = taskRepository.findByMeetingId(meeting.getId()).stream().filter(t -> t.getLevel() >= 2).toList();
        if (!tasks.isEmpty()) {
            sb.append("\n## Công việc của cuộc họp\n");
            for (MeetingTask t : tasks) {
                sb.append("- ").append(t.getTitle());
                if (notBlank(t.getAssigneeUserName())) sb.append(" — phụ trách: ").append(fullName(people, t.getAssigneeUserName()));
                if (t.getDueDate() != null) sb.append(" — hạn: ").append(vn(t.getDueDate(), VN_DATE));
                sb.append(t.getStatus() == 2 ? " — đã hoàn thành" : "").append('\n');
            }
        }
        if (published != null) {
            sb.append("\n## Biên bản đã phát hành (nguồn chính thức)\nTóm tắt: ").append(published.summary()).append('\n');
            sb.append("Quyết định:\n").append(decisionsText(published)).append('\n');
            sb.append("Việc cần làm:\n").append(actionsText(published)).append('\n');
        }
        sb.append("\n## Transcript (lời nói trong cuộc họp)\n");
        if (segs.isEmpty()) {
            sb.append("(Cuộc họp chưa có transcript.)\n");
        } else {
            LocalDateTime base = startOf(meeting);
            for (MeetingTranscriptSegment s : segs) {
                sb.append('[').append(MinutesAiGenerator.offset(base, s.getSpokenAt())).append("] ")
                        .append(notBlank(s.getSpeakerName()) ? s.getSpeakerName() : "Người tham gia").append(": ")
                        .append(s.getText().replace('\n', ' ')).append('\n');
            }
        }
        return sb.toString();
    }

    /** Mã băm của mẫu + dữ liệu cuộc họp: chưa đổi thì dùng lại file đã dựng. */
    private String dataHash(Ctx c) {
        MeetingInfo m = c.meeting();
        StringBuilder sb = new StringBuilder(MinutesTemplateGenerator.PROMPT_VERSION).append('|');
        sb.append(meetingTemplate(m.getId()).map(CmFile::getId).orElseGet(() -> systemTemplateObject(m).orElse("BUILTIN"))).append('|');
        sb.append(m.getName()).append('|').append(startOf(m)).append('|').append(m.getEndDate()).append('|');
        c.people().stream().map(p -> p.getUserName() + ":" + p.getType()).sorted().forEach(x -> sb.append(x).append(','));
        sb.append('|').append(MinutesServiceImpl.transcriptHash(segmentRepository.findByMeetingIdOrderBySpokenAtAsc(m.getId())));
        sb.append('|').append(minutesRepository.findFirstByMeetingIdAndStatusOrderByVersionDesc(m.getId(), MtMinutes.PUBLISHED)
                .map(MtMinutes::getId).orElse("-"));
        taskRepository.findByMeetingId(m.getId()).stream().filter(t -> t.getLevel() >= 2)
                .map(t -> t.getId() + ":" + t.getTitle() + ":" + t.getStatus() + ":" + t.getAssigneeUserName() + ":" + t.getDueDate())
                .sorted().forEach(x -> sb.append('|').append(x));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ===================== Định dạng =====================

    private static LocalDateTime startOf(MeetingInfo m) {
        return m.getStartDate() != null ? m.getStartDate() : m.getExpectedStartTime();
    }

    private static String timeText(MeetingInfo m) {
        LocalDateTime start = startOf(m);
        LocalDateTime end = m.getEndDate() != null ? m.getEndDate() : m.getExpectedEndTime();
        if (start == null) return MinutesTemplateRenderer.NO_INFO;
        return vn(start, VN_TIME) + (end == null ? "" : " – " + vn(end, VN_TIME));
    }

    private static String vn(LocalDateTime utc, DateTimeFormatter f) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE).format(f);
    }

    private static String names(List<MeetingPersonal> people, int type) {
        String s = people.stream().filter(p -> p.getType() == type).map(MinutesExportServiceImpl::display).collect(Collectors.joining(", "));
        return s.isBlank() ? MinutesTemplateRenderer.NO_INFO : s;
    }

    private static String participantsText(List<MeetingPersonal> people, boolean oneLine) {
        List<String> items = people.stream().filter(p -> !RoomAdmission.isGuest(p.getUserName()))
                .map(p -> display(p) + switch (p.getType()) {
                    case 1 -> " (Chủ trì)";
                    case 2 -> " (Đồng chủ trì)";
                    case 3 -> " (Thư ký)";
                    default -> "";
                }).toList();
        if (items.isEmpty()) return MinutesTemplateRenderer.NO_INFO;
        return oneLine ? String.join("; ", items) : items.stream().map(i -> "- " + i).collect(Collectors.joining("\n"));
    }

    private static String display(MeetingPersonal p) {
        return notBlank(p.getFullName()) ? p.getFullName() : p.getUserName();
    }

    private static String fullName(List<MeetingPersonal> people, String userName) {
        return people.stream().filter(p -> p.getUserName().equalsIgnoreCase(userName)).map(MinutesExportServiceImpl::display)
                .findFirst().orElse(userName);
    }

    static String decisionsText(MinutesContent c) {
        if (c.decisions().isEmpty()) return "Không có";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.decisions().size(); i++) sb.append(i + 1).append(". ").append(c.decisions().get(i).text()).append('\n');
        return sb.toString().trim();
    }

    static String actionsText(MinutesContent c) {
        if (c.actions().isEmpty()) return "Không có";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.actions().size(); i++) {
            MinutesContent.ActionItem a = c.actions().get(i);
            sb.append(i + 1).append(". ").append(a.text())
                    .append(" — Phụ trách: ").append(a.assigneeName() != null ? a.assigneeName() : a.assignee() != null ? a.assignee() : "Chưa xác định")
                    .append(" — Hạn: ").append(a.dueDate() != null ? LocalDate.parse(a.dueDate()).format(VN_DATE) : "Chưa xác định")
                    .append('\n');
        }
        return sb.toString().trim();
    }

    // ===================== Tiện ích =====================

    private MinutesContent readContent(String json) {
        try {
            return json == null || json.isBlank() ? MinutesContent.empty() : mapper.readValue(json, MinutesContent.class);
        } catch (Exception e) {
            return MinutesContent.empty();
        }
    }

    private byte[] readObject(String objectName) {
        try (InputStream in = storage.download(objectName)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được file từ kho lưu trữ.");
        }
    }

    private void removeFile(CmFile f) {
        try {
            storage.delete(f.getObjectName());
        } catch (RuntimeException e) {
            log.warn("Không xóa được object {}: {}", f.getObjectName(), e.getMessage());
        }
        fileRepository.delete(f);
    }

    private CmFile newFile(String id, String name, long size, String mime, String ext, int type, String meetingId, String objectName, String user) {
        LocalDateTime now = LocalDateTime.now(clock);
        CmFile f = new CmFile();
        f.setId(id);
        f.setFileName(name);
        f.setFileSize(BigDecimal.valueOf(size));
        f.setMimeType(mime);
        f.setExtension(ext);
        f.setType(type);
        f.setIcon("doc");
        f.setReferenceFileId(meetingId);
        f.setMinutesFile(false);
        f.setOrderNumber(0);
        f.setVoiceToText("");
        f.setBucketName(storage.getBucketName());
        f.setObjectName(objectName);
        f.setCreateBy(user);
        f.setCreateDate(now);
        f.setUpdateBy(user);
        f.setUpdateDate(now);
        return f;
    }

    private static String cleanName(String original) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").trim();
        if (n.isEmpty()) n = "mau-bien-ban";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
