package vn.com.d2s.smr.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.dto.mt.minutes.MinutesDetailResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.entity.mt.MeetingAuditLog;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.entity.mt.MtAttendanceEvent;
import vn.com.d2s.smr.entity.mt.MtMinutes;
import vn.com.d2s.smr.entity.mt.MtTaskCardLabel;
import vn.com.d2s.smr.entity.mt.MtTaskChecklistItem;
import vn.com.d2s.smr.entity.mt.MtTaskComment;
import vn.com.d2s.smr.entity.mt.MtTaskLabel;
import vn.com.d2s.smr.entity.nt.Notification;
import vn.com.d2s.smr.entity.sy.SyAuditLog;
import vn.com.d2s.smr.entity.sy.SyBackgroundJob;
import vn.com.d2s.smr.repository.sy.SyBackgroundJobRepository;
import vn.com.d2s.smr.seed.DemoPlan.PlannedMeeting;
import vn.com.d2s.smr.seed.DemoPlan.Status;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.minutes.MinutesExportService;
import vn.com.d2s.smr.service.mt.minutes.MinutesService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nạp / gỡ bộ dữ liệu demo (Plan_DuLieuDemo.md). Chỉ chạy khi bật profile {@code seed}:
 * <pre>
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed -Dspring-boot.run.arguments=--smr.seed.action=check
 * </pre>
 * action:
 * <ul>
 *   <li>{@code check} (mặc định) — chỉ đọc: kiểm tra nội dung + tài khoản trong DB, in số lượng sẽ tạo.</li>
 *   <li>{@code load} — nạp dữ liệu nền (cần biến môi trường {@code SEED_PASSWORD} cho tài khoản mới). Đã có thì bỏ qua.</li>
 *   <li>{@code minutes} — sinh biên bản bằng AI thật cho các cuộc họp có transcript, đẩy qua quy trình duyệt.</li>
 *   <li>{@code remove} — gỡ sạch mọi thứ bộ seed đã tạo (Id bắt đầu bằng {@code demo_}, tài khoản CreateBy = seed...).</li>
 * </ul>
 * Chạy xong tự thoát. Mọi Id cố định theo nội dung → chạy lại không nhân đôi; muốn làm mới ngày giờ thì remove rồi load.
 */
@Component
@Profile("seed")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String SEED = DemoPlan.SEED_USER;
    static final String SEED_LOCK = "seed-lock";
    static final String TITLE_PERMISSION_JSON = "{\"version\":1,\"permissions\":{\"AI_SUMMARY\":\"Allow\",\"MEETING_RECORD\":\"Allow\"}}";
    static final String DENY_AI_JSON = "{\"version\":1,\"permissions\":{\"AI_SUMMARY\":\"Deny\"}}";
    private static final String LIKE_DEMO = "demo!_%";

    private final EntityManager em;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;
    private final MinutesService minutesService;
    private final MinutesExportService exportService;
    private final SyBackgroundJobRepository jobRepository;
    private final ApplicationContext context;
    private final String action;
    private final String seedPassword;

    private DemoData data;
    private LocalDateTime now;
    private Map<String, AdAccount> accounts;
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    public DemoDataSeeder(EntityManager em, TransactionTemplate tx, PasswordEncoder passwordEncoder, ObjectStorageService storage,
                          ObjectMapper mapper, MinutesService minutesService, MinutesExportService exportService,
                          SyBackgroundJobRepository jobRepository, ApplicationContext context,
                          @Value("${smr.seed.action:check}") String action,
                          @Value("${SEED_PASSWORD:}") String seedPassword) {
        this.em = em;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
        this.mapper = mapper;
        this.minutesService = minutesService;
        this.exportService = exportService;
        this.jobRepository = jobRepository;
        this.context = context;
        this.action = action == null ? "check" : action.trim().toLowerCase();
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try {
            data = DemoData.load(mapper);
            now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
            log.info("=== Bộ dữ liệu demo: action = {} ===", action);
            switch (action) {
                case "check" -> check();
                case "load" -> load();
                case "minutes" -> minutes();
                case "remove" -> remove();
                default -> throw new IllegalArgumentException("smr.seed.action phải là check | load | minutes | remove");
            }
            counts.forEach((k, v) -> log.info("  {}: {}", k, v));
            log.info("=== Xong ({}) ===", action);
        } catch (Exception e) {
            log.error("Bộ dữ liệu demo lỗi: {}", e.getMessage(), e);
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }

    // =====================================================================================
    // check
    // =====================================================================================

    void check() {
        loadAccounts();
        List<String> problems = validate();
        List<PlannedMeeting> plan = DemoPlan.plan(data, now);
        counts.put("Cuộc họp sẽ tạo", plan.size());
        counts.put("  trong đó đã có trong DB", (int) plan.stream().filter(p -> em.find(MeetingInfo.class, p.id()) != null).count());
        counts.put("Tài khoản mới sẽ tạo", (int) data.accounts().stream().filter(a -> !accounts.containsKey(key(a.userName()))).count());
        counts.put("Đoạn transcript", data.transcripts().values().stream().mapToInt(List::size).sum());
        counts.put("Thẻ công việc", data.boards().stream().flatMap(b -> b.lists().stream()).mapToInt(l -> l.cards().size()).sum());
        if (!problems.isEmpty()) {
            problems.forEach(p -> log.warn("  - {}", p));
            throw new IllegalStateException(problems.size() + " vấn đề trong nội dung demo (xem log).");
        }
        log.info("Nội dung hợp lệ, mọi tài khoản tham chiếu đều có (hoặc sẽ được tạo).");
    }

    /** Người được nhắc tới phải là tài khoản có trong DB hoặc trong danh sách tạo mới; người nói phải là thành viên. */
    List<String> validate() {
        Set<String> users = new HashSet<>(accounts.keySet());
        data.accounts().forEach(a -> users.add(key(a.userName())));
        List<String> problems = new ArrayList<>();
        for (PlannedMeeting p : DemoPlan.plan(data, now)) {
            Map<String, Integer> members = p.members();
            members.keySet().stream().filter(u -> !users.contains(key(u))).forEach(u -> problems.add(p.source().key() + ": không có tài khoản " + u));
            String t = p.source().transcript();
            if (t != null) {
                if (!data.transcripts().containsKey(t)) problems.add(p.source().key() + ": thiếu transcript " + t);
                else data.transcripts().get(t).stream().filter(l -> !members.containsKey(l.speaker()))
                        .forEach(l -> problems.add(p.source().key() + ": người nói " + l.speaker() + " không thuộc cuộc họp"));
            }
        }
        for (DemoData.Board b : data.boards()) {
            if (!users.contains(key(b.owner()))) problems.add("Bảng " + b.key() + ": không có tài khoản " + b.owner());
            b.lists().forEach(l -> l.cards().stream().filter(c -> c.assignee() != null && !users.contains(key(c.assignee())))
                    .forEach(c -> problems.add("Bảng " + b.key() + ": không có tài khoản " + c.assignee())));
        }
        return problems;
    }

    // =====================================================================================
    // load
    // =====================================================================================

    void load() {
        if (seedPassword == null || seedPassword.length() < 8) {
            throw new IllegalStateException("Đặt biến môi trường SEED_PASSWORD (≥ 8 ký tự) làm mật khẩu cho tài khoản demo mới.");
        }
        tx.executeWithoutResult(s -> seedAccounts());
        loadAccounts();
        List<String> problems = validate();
        if (!problems.isEmpty()) {
            problems.forEach(p -> log.warn("  - {}", p));
            throw new IllegalStateException("Nội dung demo chưa hợp lệ, dừng trước khi ghi cuộc họp.");
        }
        List<PlannedMeeting> plan = DemoPlan.plan(data, now);
        Map<String, PlannedMeeting> byKey = plan.stream().collect(Collectors.toMap(p -> p.source().key(), Function.identity()));
        tx.executeWithoutResult(s -> plan.forEach(this::seedMeeting));
        tx.executeWithoutResult(s -> data.boards().forEach(b -> seedBoard(b, byKey)));
        seedFiles(plan, byKey);
        seedNotificationsAndAudit(plan);
    }

    private void seedAccounts() {
        loadAccounts();
        for (DemoData.Account a : data.accounts()) {
            if (accounts.containsKey(key(a.userName()))) continue;
            AdAccount acc = new AdAccount();
            acc.setUserName(a.userName());
            acc.setPassword(passwordEncoder.encode(seedPassword));
            acc.setFullName(a.fullName());
            acc.setPhone(a.phone());
            acc.setEmail(a.userName() + "@" + data.emailDomain());
            acc.setAddress("");
            acc.setOrgId(a.orgId());
            acc.setTitleCode(a.titleCode());
            acc.setRoleCodes("USER");
            acc.setActive(!a.locked());
            acc.setMustChangePassword(a.mustChangePassword());
            if (a.denyAiSummary()) acc.setPermissionJson(DENY_AI_JSON);
            stamp(acc, SEED, now.minusDays(a.mustChangePassword() ? 2 : 60));
            em.persist(acc);
            inc("Tài khoản mới");
        }
        for (String u : data.junkAccounts()) {
            AdAccount acc = em.find(AdAccount.class, u);
            if (acc != null && acc.isActive()) {
                acc.setActive(false);
                acc.setUpdateBy(SEED_LOCK);
                acc.setUpdateDate(now);
                inc("Tài khoản rác đã khóa");
            }
        }
        for (String code : data.managerTitles()) {
            MdTitle t = em.find(MdTitle.class, code);
            if (t != null && (t.getPermissionJson() == null || t.getPermissionJson().isBlank())) {
                t.setPermissionJson(TITLE_PERMISSION_JSON);
                t.setUpdateBy(SEED);
                t.setUpdateDate(now);
                inc("Chức danh được cấp AI_SUMMARY, MEETING_RECORD");
            }
        }
    }

    private void seedMeeting(PlannedMeeting p) {
        MeetingInfo existing = em.find(MeetingInfo.class, p.id());
        if (existing != null) {
            inc("Cuộc họp đã có (bỏ qua)");
            // Transcript mới thêm vào JSON sau lần nạp đầu → bổ sung cho cuộc họp đã có, theo giờ họp thực tế trong DB.
            if (p.source().transcript() != null && existing.getStartDate() != null && existing.getEndDate() != null
                    && em.createQuery("select s.id from MeetingTranscriptSegment s where s.meetingId = :id")
                    .setParameter("id", p.id()).setMaxResults(1).getResultList().isEmpty()) {
                transcript(p, existing.getStartDate(), existing.getEndDate());
            }
            return;
        }
        DemoData.Meeting src = p.source();
        Random rnd = new Random(p.id().hashCode());
        boolean happened = p.status() == Status.ENDED || p.status() == Status.ARCHIVED;
        LocalDateTime created = p.startUtc().minusDays(p.status() == Status.ONGOING ? 1 : 6).minusHours(rnd.nextInt(5));
        if (created.isAfter(now)) created = now.minusHours(2);
        if (p.status() == Status.SCHEDULED || p.status() == Status.DRAFT) {
            created = min(created, now.minusDays(1 + rnd.nextInt(3)));
        }
        LocalDateTime actualStart = happened ? p.startUtc().plusMinutes(rnd.nextInt(4))
                : p.status() == Status.ONGOING ? p.startUtc() : null;
        LocalDateTime actualEnd = happened ? p.endUtc().plusMinutes(rnd.nextInt(14) - 5) : null;

        MeetingInfo m = new MeetingInfo();
        m.setId(p.id());
        m.setName(src.name());
        m.setMeetContent(nz(src.description()));
        m.setAgenda(nz(src.agenda()));
        m.setExpectedStartTime(p.startUtc());
        m.setExpectedEndTime(p.endUtc());
        m.setStartDate(actualStart);
        m.setEndDate(actualEnd);
        m.setStatus(p.status().code);
        m.setVisibility(src.visibility() == null ? 1 : src.visibility());
        String room = DemoPlan.roomCode(src.key());
        m.setRoomCode(room);
        m.setJoinUrl("/meeting/" + room);
        m.setReferenceFileId(refId(p));
        m.setSettingsJson(settingsJson(src));
        m.setDraft(p.status() == Status.DRAFT);
        m.setArchived(p.status() == Status.ARCHIVED);
        m.setDeleted(false);
        m.setCancellationReason(p.status() == Status.CANCELLED ? nz(src.cancelReason()) : "");
        m.setNotes("");
        stamp(m, src.host(), created);
        if (happened) m.setUpdateDate(actualEnd);
        em.persist(m);
        inc("Cuộc họp");

        Map<String, Integer> members = p.members();
        DemoData.Attendance att = src.attendance();
        for (Map.Entry<String, Integer> e : members.entrySet()) {
            AdAccount a = account(e.getKey());
            boolean present = (happened || p.status() == Status.ONGOING) && !att.absent().contains(e.getKey());
            LocalDateTime enter = present ? actualStart.plusMinutes(att.late().getOrDefault(e.getKey(), rnd.nextInt(3))) : null;
            MeetingPersonal mp = new MeetingPersonal();
            mp.setId(DemoPlan.id("p", src.key() + ":" + e.getKey()));
            mp.setMeetingId(p.id());
            mp.setUserName(a.getUserName());
            mp.setFullName(a.getFullName());
            mp.setPhone(nz(a.getPhone()));
            mp.setEmail(nz(a.getEmail()));
            mp.setAddress(nz(a.getAddress()));
            mp.setOrgId(nz(a.getOrgId()));
            mp.setTitleCode(nz(a.getTitleCode()));
            mp.setReferenceFileId(refId(p));
            mp.setType(e.getValue());
            mp.setChairperson(e.getValue() == 1 || e.getValue() == 2);
            mp.setJoined(present);
            mp.setJoinTime(enter);
            stamp(mp, src.host(), created);
            em.persist(mp);
            inc("Người tham gia");
            if (present) attendance(p, e.getKey(), enter, actualEnd, att);
        }

        audit(p, "CREATE", src.host(), created);
        if (p.status() != Status.DRAFT) audit(p, "PUBLISH", src.host(), created.plusMinutes(1));
        if (actualStart != null) audit(p, "START", src.host(), actualStart);
        if (actualEnd != null) audit(p, "END", src.host(), actualEnd);
        if (p.status() == Status.CANCELLED) audit(p, "CANCEL", src.host(), min(p.startUtc().minusHours(20), now.minusHours(3)));

        if (src.transcript() != null && happened) transcript(p, actualStart, actualEnd);
    }

    private void attendance(PlannedMeeting p, String user, LocalDateTime enter, LocalDateTime end, DemoData.Attendance att) {
        int n = 0;
        event(p, user, n++, MtAttendanceEvent.ENTER, enter);
        if (att.reconnect().contains(user) && end != null) {
            LocalDateTime mid = enter.plus(Duration.between(enter, end).dividedBy(2));
            event(p, user, n++, MtAttendanceEvent.LEAVE, mid);
            event(p, user, n++, MtAttendanceEvent.ENTER, mid.plusMinutes(3));
        }
        if (end != null) event(p, user, n, MtAttendanceEvent.LEAVE, end.minusMinutes(att.leaveEarly().getOrDefault(user, 0)));
    }

    private void event(PlannedMeeting p, String user, int n, int type, LocalDateTime at) {
        MtAttendanceEvent e = new MtAttendanceEvent();
        e.setId(DemoPlan.id("ae", p.source().key() + ":" + user + ":" + n));
        e.setMeetingId(p.id());
        e.setUserName(user);
        e.setEventType(type);
        e.setOccurredAt(at);
        e.setClientSessionId("seed-" + user);
        em.persist(e);
        inc("Sự kiện tham dự");
    }

    private void audit(PlannedMeeting p, String action, String actor, LocalDateTime at) {
        MeetingAuditLog a = new MeetingAuditLog();
        a.setId(DemoPlan.id("ma", p.source().key() + ":" + action));
        a.setMeetingId(p.id());
        a.setAction(action);
        a.setActorId(actor);
        a.setOccurredAt(at);
        a.setCorrelationId("");
        a.setVersion(1);
        a.setPayloadJson("");
        em.persist(a);
    }

    private void transcript(PlannedMeeting p, LocalDateTime start, LocalDateTime end) {
        List<DemoData.Line> lines = data.transcripts().get(p.source().transcript());
        Random rnd = new Random(p.id().hashCode() * 31L);
        long span = Math.max(60, Duration.between(start, end).getSeconds() - 120);
        for (int i = 0; i < lines.size(); i++) {
            DemoData.Line l = lines.get(i);
            long sec = 30 + (span * i) / lines.size() + rnd.nextInt(20);
            MeetingTranscriptSegment s = new MeetingTranscriptSegment();
            s.setId(DemoPlan.id("s", p.source().key() + ":" + i));
            s.setMeetingId(p.id());
            s.setSpeakerUserName(l.speaker());
            s.setSpeakerName(account(l.speaker()).getFullName());
            s.setText(DemoPlan.fillDates(l.text(), p.localDate()));
            s.setLanguage("vi-VN");
            s.setSpokenAt(start.plusSeconds(sec));
            em.persist(s);
            inc("Đoạn transcript");
        }
    }

    // ----- Bảng công việc

    private void seedBoard(DemoData.Board b, Map<String, PlannedMeeting> meetings) {
        String boardId = DemoPlan.id("t", b.key());
        if (em.find(MeetingTask.class, boardId) != null) {
            inc("Bảng công việc đã có (bỏ qua)");
            return;
        }
        PlannedMeeting meeting = b.meeting() == null ? null : meetings.get(b.meeting());
        String meetingId = meeting == null ? null : meeting.id();
        LocalDateTime boardCreated = meeting != null ? min(meeting.endUtc(), now.minusDays(1)) : now.minusDays(45);
        MeetingTask board = task(boardId, null, 0, b.title(), meetingId, b.owner(), boardCreated, 0);
        board.setDescription(b.description());
        board.setColor(b.color());
        board.setPublicTask(b.isPublic());
        em.persist(board);
        inc("Bảng công việc");

        Map<String, String> labelIds = new LinkedHashMap<>();
        for (List<String> l : b.labels()) {
            MtTaskLabel label = new MtTaskLabel();
            label.setId(DemoPlan.id("l", b.key() + ":" + l.get(0)));
            label.setWorkId(boardId);
            label.setName(l.get(0));
            label.setColor(l.get(1));
            em.persist(label);
            labelIds.put(l.get(0), label.getId());
        }
        for (List<Object> s : b.shares()) {
            MeetingTaskShare share = new MeetingTaskShare();
            share.setId(DemoPlan.id("sh", b.key() + ":" + s.get(0)));
            share.setTaskId(boardId);
            share.setUserName(String.valueOf(s.get(0)));
            share.setPermission(((Number) s.get(1)).intValue());
            stamp(share, b.owner(), boardCreated.plusHours(1));
            em.persist(share);
        }
        int listOrder = 0;
        for (DemoData.TaskList list : b.lists()) {
            String listId = DemoPlan.id("t", b.key() + ":" + list.title());
            em.persist(task(listId, boardId, 1, list.title(), meetingId, b.owner(), boardCreated, listOrder++));
            int order = 0;
            for (DemoData.Card c : list.cards()) {
                String cardId = DemoPlan.id("t", b.key() + ":" + list.title() + ":" + c.title());
                LocalDateTime due = c.due() == null ? null : DemoPlan.dayAt(now, c.due(), 17);
                LocalDateTime start = c.start() == null ? null : DemoPlan.dayAt(now, c.start(), 8);
                LocalDateTime created = min(start != null ? start.minusDays(1) : due != null ? due.minusDays(10) : now.minusDays(20), now.minusHours(6));
                created = max(created, boardCreated);
                MeetingTask card = task(cardId, listId, 2, c.title(), meetingId, b.owner(), created, order++);
                card.setDescription(c.description());
                card.setAssigneeUserName(c.assignee());
                card.setDueDate(due);
                card.setStartDate(start);
                card.setStatus(c.status());
                card.setPriority(Math.max(1, c.priority()));
                if (c.status() == 2) card.setUpdateDate(min(due != null ? due.minusHours(3) : now.minusDays(1), now.minusHours(1)));
                em.persist(card);
                inc("Thẻ công việc");
                for (String ln : c.labels()) {
                    String lid = labelIds.get(ln);
                    if (lid == null) continue;
                    MtTaskCardLabel cl = new MtTaskCardLabel();
                    cl.setTaskId(cardId);
                    cl.setLabelId(lid);
                    em.persist(cl);
                }
                int ci = 0;
                for (List<Object> item : c.checklist()) {
                    MtTaskChecklistItem it = new MtTaskChecklistItem();
                    it.setId(DemoPlan.id("ck", cardId + ":" + ci));
                    it.setTaskId(cardId);
                    it.setContent(String.valueOf(item.get(0)));
                    it.setDone(Boolean.TRUE.equals(item.get(1)));
                    it.setSortOrder(ci++);
                    em.persist(it);
                }
                int mi = 0;
                for (List<Object> cm : c.comments()) {
                    MtTaskComment comment = new MtTaskComment();
                    comment.setId(DemoPlan.id("cm", cardId + ":" + mi++));
                    comment.setTaskId(cardId);
                    comment.setUserName(String.valueOf(cm.get(0)));
                    comment.setContent(String.valueOf(cm.get(1)));
                    comment.setCreateDate(min(DemoPlan.dayAt(now, ((Number) cm.get(2)).intValue(), 10), now.minusMinutes(30)));
                    em.persist(comment);
                }
            }
        }
    }

    private MeetingTask task(String id, String parentId, int level, String title, String meetingId, String user, LocalDateTime at, int order) {
        MeetingTask t = new MeetingTask();
        t.setId(id);
        t.setParentId(parentId);
        t.setLevel(level);
        t.setTitle(title);
        t.setMeetingId(meetingId);
        t.setStatus(0);
        t.setPriority(1);
        t.setSortOrder(order);
        stamp(t, user, at);
        return t;
    }

    // ----- Tài liệu, mẫu biên bản

    private void seedFiles(List<PlannedMeeting> plan, Map<String, PlannedMeeting> byKey) {
        for (PlannedMeeting p : plan) {
            for (String name : p.source().files()) {
                uploadFile(DemoPlan.id("f", p.source().key() + ":" + name), name, 1, refId(p), p.source().host(),
                        min(p.startUtc().minusDays(1), now.minusHours(2)), "demo/meetings/" + p.source().key() + "/" + name, p.source().name(), null);
            }
        }
        for (DemoData.Board b : data.boards()) {
            for (DemoData.TaskList list : b.lists()) {
                for (DemoData.Card c : list.cards()) {
                    if (c.attachment() == null) continue;
                    String cardId = DemoPlan.id("t", b.key() + ":" + list.title() + ":" + c.title());
                    uploadFile(DemoPlan.id("f", cardId + ":" + c.attachment()), c.attachment(), 3, cardId,
                            c.assignee() != null ? c.assignee() : b.owner(), now.minusDays(3), "demo/tasks/" + b.key() + "/" + c.attachment(), c.title(), null);
                }
            }
        }
        // Mẫu biên bản riêng của cuộc họp (Type 6, RefrenceFileId = meetingId).
        data.meetingTemplates().forEach((meetingKey, fileName) -> {
            PlannedMeeting p = byKey.get(meetingKey);
            if (p == null) return;
            try {
                byte[] bytes = fileName.endsWith(".pdf") ? DemoFiles.pdfTemplate(data.company()) : DemoFiles.docxTemplate(data.company());
                uploadFile(DemoPlan.id("f", "tpl:" + meetingKey), fileName, 6, p.id(), p.source().secretary() != null ? p.source().secretary() : p.source().host(),
                        min(p.endUtc(), now.minusHours(2)), "demo/templates/" + meetingKey + "/" + fileName, null, bytes);
            } catch (IOException e) {
                throw new IllegalStateException("Không tạo được mẫu biên bản " + fileName, e);
            }
        });
    }

    private void uploadFile(String id, String name, int type, String reference, String user, LocalDateTime at, String objectName,
                            String title, byte[] fixedBytes) {
        if (Boolean.TRUE.equals(tx.execute(s -> em.find(CmFile.class, id) != null))) return;
        DemoFiles.Generated g = fixedBytes != null
                ? new DemoFiles.Generated(fixedBytes, name.endsWith(".pdf") ? "application/pdf" : DemoFiles.DOCX, name.substring(name.lastIndexOf('.')))
                : DemoFiles.create(name, title, data.company());
        storage.upload(objectName, new ByteArrayInputStream(g.bytes()), g.bytes().length, g.mime());
        tx.executeWithoutResult(s -> {
            CmFile f = new CmFile();
            f.setId(id);
            f.setFileName(name);
            f.setFileSize(BigDecimal.valueOf(g.bytes().length));
            f.setMimeType(g.mime());
            f.setExtension(g.extension());
            f.setType(type);
            f.setIcon(switch (g.extension()) {
                case ".pdf" -> "pdf";
                case ".xlsx" -> "xls";
                case ".png" -> "img";
                default -> "doc";
            });
            f.setReferenceFileId(reference);
            f.setMinutesFile(false);
            f.setOrderNumber(0);
            f.setVoiceToText("");
            f.setBucketName(storage.getBucketName());
            f.setObjectName(objectName);
            stamp(f, user, at);
            em.persist(f);
        });
        inc("Tài liệu (MinIO)");
    }

    // ----- Thông báo, nhật ký

    /** Gom bản ghi rồi ghi theo lô 100 dòng / giao dịch (DB ở xa, giao dịch dài dễ bị ngắt kết nối). */
    private final List<Object> pending = new ArrayList<>();

    private void seedNotificationsAndAudit(List<PlannedMeeting> plan) {
        pending.clear();
        collectNotificationsAndAudit(plan);
        Set<String> existing = new HashSet<>(tx.execute(s -> {
            List<String> ids = new ArrayList<>(em.createQuery("select n.id from Notification n where n.id like :p escape '!'", String.class)
                    .setParameter("p", LIKE_DEMO).getResultList());
            ids.addAll(em.createQuery("select a.id from SyAuditLog a where a.id like :p escape '!'", String.class)
                    .setParameter("p", LIKE_DEMO).getResultList());
            return ids;
        }));
        List<Object> todo = pending.stream().filter(o -> !existing.contains(o instanceof Notification n ? n.getId() : ((SyAuditLog) o).getId())).toList();
        for (int i = 0; i < todo.size(); i += 100) {
            List<Object> batch = todo.subList(i, Math.min(i + 100, todo.size()));
            tx.executeWithoutResult(s -> batch.forEach(em::persist));
        }
        todo.forEach(o -> inc(o instanceof Notification ? "Thông báo" : "Nhật ký hoạt động"));
    }

    private void collectNotificationsAndAudit(List<PlannedMeeting> plan) {
        for (PlannedMeeting p : plan) {
            DemoData.Meeting src = p.source();
            LocalDateTime created = em.find(MeetingInfo.class, p.id()).getCreateDate();
            boolean recent = p.startUtc().isAfter(now.minusDays(14));
            if (p.status() == Status.SCHEDULED || (recent && p.status() != Status.DRAFT && p.status() != Status.CANCELLED)) {
                for (String u : p.members().keySet()) {
                    if (u.equalsIgnoreCase(src.host())) continue;
                    notify("inv:" + src.key() + ":" + u, u, "MEETING_INVITED", 0, account(src.host()).getFullName() + " mời bạn tham gia cuộc họp",
                            src.name(), "meeting", p.id(), p.id(), src.host(), meetingData(p), "MEETING_INVITED:" + p.id(), created.plusMinutes(1));
                }
            }
            if (p.status() == Status.CANCELLED) {
                for (String u : p.members().keySet()) {
                    if (u.equalsIgnoreCase(src.host())) continue;
                    notify("can:" + src.key() + ":" + u, u, "MEETING_CANCELLED", 1, "Cuộc họp đã bị hủy", src.name(), "meeting", p.id(), p.id(),
                            src.host(), meetingData(p), "MEETING_CANCELLED:" + p.id(), min(p.startUtc().minusHours(20), now.minusHours(3)));
                }
            }
            audit("mc:" + src.key(), "MEETING", "CREATE", src.host(), "MEETING", p.id(), true, created);
            if (p.status() == Status.CANCELLED) {
                audit("mx:" + src.key(), "MEETING", "CANCEL", src.host(), "MEETING", p.id(), true, min(p.startUtc().minusHours(20), now.minusHours(3)));
            }
        }
        for (DemoData.Board b : data.boards()) {
            String boardId = DemoPlan.id("t", b.key());
            for (DemoData.TaskList list : b.lists()) {
                for (DemoData.Card c : list.cards()) {
                    if (c.assignee() == null || c.assignee().equalsIgnoreCase(b.owner())) continue;
                    String cardId = DemoPlan.id("t", b.key() + ":" + list.title() + ":" + c.title());
                    MeetingTask card = em.find(MeetingTask.class, cardId);
                    if (card == null || card.getCreateDate().isBefore(now.minusDays(21))) continue;
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("schemaVersion", 1);
                    d.put("level", 2);
                    d.put("parentId", card.getParentId());
                    d.put("workId", boardId);
                    d.put("workTitle", b.title());
                    notify("ta:" + cardId, c.assignee(), "TASK_ASSIGNED", 0, account(b.owner()).getFullName() + " giao cho bạn một việc",
                            c.title(), "task", cardId, card.getMeetingId(), b.owner(), d, "TASK_ASSIGNED:" + cardId, card.getCreateDate());
                }
            }
        }
        // Đăng nhập: mỗi tài khoản đang hoạt động đăng nhập vài ngày làm việc trong 14 ngày qua; vài lần nhập sai mật khẩu.
        for (AdAccount a : accounts.values()) {
            if (!a.isActive()) continue;
            Random rnd = new Random(a.getUserName().hashCode());
            for (int d = 1; d <= 14; d++) {
                LocalDateTime day = DemoPlan.dayAt(now, -d, 8).plusMinutes(rnd.nextInt(80));
                int dow = day.atOffset(ZoneOffset.UTC).atZoneSameInstant(vn.com.d2s.smr.config.BusinessTime.ZONE).getDayOfWeek().getValue();
                if (dow >= 6 || rnd.nextInt(10) < 4) continue;
                audit("login:" + a.getUserName() + ":" + d, "AUTH", "LOGIN", a.getUserName(), "ACCOUNT", a.getUserName(), true, day);
            }
        }
        for (String u : List.of("phong.nguyen", "mai.dang", "giang.le")) {
            if (accounts.containsKey(u)) audit("loginfail:" + u, "AUTH", "LOGIN", u, "ACCOUNT", u, false, DemoPlan.dayAt(now, -3, 8).plusMinutes(7));
        }
        for (String code : data.managerTitles()) {
            MdTitle t = em.find(MdTitle.class, code);
            if (t != null && TITLE_PERMISSION_JSON.equals(t.getPermissionJson())) {
                audit("perm:" + code, "PERMISSION", "UPDATE", "admin", "TITLE", code, true, now.minusDays(30));
            }
        }
    }

    private Map<String, Object> meetingData(PlannedMeeting p) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("schemaVersion", 1);
        d.put("meetingName", p.source().name());
        d.put("startTime", p.startUtc().toInstant(ZoneOffset.UTC).toString());
        d.put("endTime", p.endUtc().toInstant(ZoneOffset.UTC).toString());
        return d;
    }

    private void notify(String key, String recipient, String type, int severity, String title, String body, String resourceType,
                        String resourceId, String meetingId, String actor, Map<String, Object> d, String dedupe, LocalDateTime at) {
        String id = DemoPlan.id("n", key);
        if (!accounts.containsKey(key(recipient))) return;
        Notification n = new Notification();
        n.setId(id);
        n.setRecipientUserName(recipient);
        n.setType(type);
        n.setSeverity(severity);
        n.setTitle(title);
        n.setBody(body);
        n.setResourceType(resourceType);
        n.setResourceId(resourceId);
        n.setMeetingId(meetingId);
        n.setActorUserName(actor);
        n.setActorFullName(account(actor).getFullName());
        n.setDataJson(json(d));
        n.setDedupeKey(dedupe);
        boolean read = at.isBefore(now.minusDays(2));
        n.setRead(read);
        n.setReadAt(read ? at.plusHours(2) : null);
        n.setCreateDate(min(at, now.minusMinutes(5)));
        pending.add(n);
    }

    private void audit(String key, String category, String action, String actor, String targetType, String targetId, boolean ok, LocalDateTime at) {
        String id = DemoPlan.id("al", key);
        SyAuditLog a = new SyAuditLog();
        a.setId(id);
        a.setCategory(category);
        a.setAction(action);
        a.setActorUserName(actor);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setSuccess(ok);
        a.setHttpStatus(ok ? 200 : 401);
        a.setIp("10.0.1." + (Math.abs(actor.hashCode()) % 200 + 20));
        a.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/129.0 Safari/537.36");
        a.setPayloadJson(null);
        a.setOccurredAt(min(at, now.minusMinutes(5)));
        pending.add(a);
    }

    // =====================================================================================
    // minutes — biên bản bằng AI thật + quy trình duyệt
    // =====================================================================================

    void minutes() {
        loadAccounts();
        List<PlannedMeeting> plan = DemoPlan.plan(data, now);
        for (PlannedMeeting p : plan) {
            DemoData.Meeting src = p.source();
            String flow = src.minutesFlow();
            if (flow == null || em.find(MeetingInfo.class, p.id()) == null) continue;
            if (Boolean.TRUE.equals(tx.execute(s -> !em.createQuery("select m.id from MtMinutes m where m.meetingId = :id")
                    .setParameter("id", p.id()).setMaxResults(1).getResultList().isEmpty()))) {
                inc("Biên bản đã có (bỏ qua)");
                continue;
            }
            try {
                if ("manual".equals(flow)) manualMinutes(p);
                else aiMinutes(p, flow);
            } catch (RuntimeException e) {
                log.warn("Biên bản cho '{}' lỗi: {}", src.name(), e.getMessage());
                inc("Biên bản lỗi");
            }
        }
        // Hai lần "Tải biên bản họp" theo mẫu riêng → có số liệu job MINUTES_EXPORT cho báo cáo xử lý AI.
        for (String meetingKey : data.meetingTemplates().keySet()) {
            PlannedMeeting p = plan.stream().filter(x -> x.source().key().equals(meetingKey)).findFirst().orElse(null);
            if (p == null) continue;
            String user = p.source().secretary() != null ? p.source().secretary() : p.source().host();
            try {
                var job = exportService.requestExport(user, p.id());
                SyBackgroundJob done = waitJob(job.id());
                log.info("Tải biên bản theo mẫu '{}': {}", p.source().name(), done.getStatus() == SyBackgroundJob.DONE ? "xong" : "lỗi " + done.getError());
                inc("Tổng hợp biên bản theo mẫu");
            } catch (RuntimeException e) {
                log.warn("Tổng hợp theo mẫu '{}' lỗi: {}", p.source().name(), e.getMessage());
            }
        }
    }

    private void aiMinutes(PlannedMeeting p, String flow) {
        DemoData.Meeting src = p.source();
        // Người sinh: chủ trì → đồng chủ trì → thư ký (người đầu tiên có quyền AI_SUMMARY).
        List<String> candidates = new ArrayList<>();
        candidates.add(src.host());
        candidates.addAll(src.cohost());
        if (src.secretary() != null) candidates.add(src.secretary());
        String generator = null;
        String jobId = null;
        for (String u : candidates) {
            try {
                jobId = minutesService.generate(u, p.id()).id();
                generator = u;
                break;
            } catch (SecurityException e) {
                log.debug("{} không sinh được biên bản: {}", u, e.getMessage());
            }
        }
        if (jobId == null) throw new IllegalStateException("không ai trong cuộc họp có quyền AI_SUMMARY");
        log.info("Đang sinh biên bản '{}' (người sinh: {})…", src.name(), generator);
        SyBackgroundJob job = waitJob(jobId);
        if (job.getStatus() != SyBackgroundJob.DONE) throw new IllegalStateException("AI lỗi: " + job.getError());
        inc("Biên bản AI");

        MinutesDetailResponse d = minutesService.detail(generator, p.id(), null);
        int version = d.current().version();
        // Bỏ các mục AI không trích được nguồn để phát hành được (quy tắc chống bịa đặt).
        MinutesContent c = d.current().content();
        MinutesContent clean = new MinutesContent(c.summary(), c.summarySources(),
                c.decisions().stream().filter(x -> !x.sources().isEmpty()).toList(),
                c.actions().stream().filter(x -> !x.sources().isEmpty()).toList());
        String editor = src.secretary() != null ? src.secretary() : src.host();
        if (clean.decisions().size() != c.decisions().size() || clean.actions().size() != c.actions().size()) {
            minutesService.update(editor, p.id(), version, clean);
        }
        switch (flow) {
            case "published" -> {
                minutesService.submit(editor, p.id(), version);
                minutesService.approve(src.host(), p.id(), version);
                minutesService.publish(src.host(), p.id(), version);
            }
            case "submitted" -> minutesService.submit(editor, p.id(), version);
            case "rejected" -> {
                minutesService.submit(editor, p.id(), version);
                minutesService.reject(src.host(), p.id(), version, src.rejectReason() != null ? src.rejectReason() : "Cần bổ sung thông tin.");
            }
            default -> { }
        }
        inc("Biên bản → " + flow);
    }

    private void manualMinutes(PlannedMeeting p) {
        DemoData.ManualMinutes mm = data.manualMinutes().get(p.source().key());
        if (mm == null) return;
        String editor = p.source().secretary() != null ? p.source().secretary() : p.source().host();
        var v = minutesService.newDraft(editor, p.id());
        List<MinutesContent.Decision> decisions = mm.decisions().stream().map(t -> new MinutesContent.Decision(null, t, List.of())).toList();
        List<MinutesContent.ActionItem> actions = mm.actions().stream().map(a -> new MinutesContent.ActionItem(null, String.valueOf(a.get(0)),
                String.valueOf(a.get(1)), null, p.localDate().plusDays(((Number) a.get(2)).intValue()).toString(), List.of(), true, null)).toList();
        minutesService.update(editor, p.id(), v.version(), new MinutesContent(mm.summary(), List.of(), decisions, actions));
        minutesService.submit(editor, p.id(), v.version());
        inc("Biên bản soạn tay");
    }

    private SyBackgroundJob waitJob(String jobId) {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();
        while (System.currentTimeMillis() < deadline) {
            SyBackgroundJob j = jobRepository.findById(jobId).orElseThrow();
            if (j.getStatus() == SyBackgroundJob.DONE || j.getStatus() == SyBackgroundJob.FAILED) return j;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Bị ngắt khi chờ job");
            }
        }
        throw new IllegalStateException("Quá 5 phút chưa xong job " + jobId);
    }

    // =====================================================================================
    // remove
    // =====================================================================================

    void remove() {
        // File trên MinIO: lấy danh sách trước rồi xoá object, sau đó mới xoá dòng.
        List<String> taskIds = tx.execute(s -> em.createQuery(
                        "select t.id from MeetingTask t where t.id like :p escape '!' or t.meetingId like :p escape '!'", String.class)
                .setParameter("p", LIKE_DEMO).getResultList());
        List<String> refIds = tx.execute(s -> em.createQuery(
                        "select m.referenceFileId from MeetingInfo m where m.id like :p escape '!'", String.class)
                .setParameter("p", LIKE_DEMO).getResultList());
        List<CmFile> files = tx.execute(s -> {
            List<CmFile> all = new ArrayList<>(em.createQuery("select f from CmFile f where f.id like :p escape '!' or f.referenceFileId like :p escape '!'", CmFile.class)
                    .setParameter("p", LIKE_DEMO).getResultList());
            if (!taskIds.isEmpty()) all.addAll(em.createQuery("select f from CmFile f where f.referenceFileId in :ids", CmFile.class)
                    .setParameter("ids", taskIds).getResultList());
            if (!refIds.isEmpty()) all.addAll(em.createQuery("select f from CmFile f where f.referenceFileId in :ids", CmFile.class)
                    .setParameter("ids", refIds).getResultList());
            return all.stream().collect(Collectors.toMap(CmFile::getId, Function.identity(), (a, b) -> a)).values().stream().toList();
        });
        for (CmFile f : files) {
            try {
                storage.delete(f.getObjectName());
            } catch (RuntimeException e) {
                log.warn("Không xoá được object {}: {}", f.getObjectName(), e.getMessage());
            }
        }
        tx.executeWithoutResult(s -> {
            if (!files.isEmpty()) del("delete from CmFile f where f.id in :ids", "ids", files.stream().map(CmFile::getId).toList(), "Tài liệu");
            if (!taskIds.isEmpty()) {
                del("delete from MtTaskCardLabel x where x.taskId in :ids", "ids", taskIds, "Nhãn gắn thẻ");
                del("delete from MtTaskChecklistItem x where x.taskId in :ids", "ids", taskIds, "Checklist");
                del("delete from MtTaskComment x where x.taskId in :ids", "ids", taskIds, "Bình luận");
                del("delete from MtTaskLabel x where x.workId in :ids", "ids", taskIds, "Nhãn");
                del("delete from MeetingTaskShare x where x.taskId in :ids", "ids", taskIds, "Chia sẻ");
                del("delete from Notification x where x.resourceId in :ids", "ids", taskIds, "Thông báo công việc");
                for (int level = 3; level >= 0; level--) {
                    int n = em.createQuery("delete from MeetingTask t where t.id in :ids and t.level = :lv")
                            .setParameter("ids", taskIds).setParameter("lv", level).executeUpdate();
                    counts.merge("Công việc", n, Integer::sum);
                }
            }
            like("delete from MtMinutes x where x.meetingId like :p escape '!'", "Biên bản");
            like("delete from SyBackgroundJob x where x.refId like :p escape '!'", "Job nền");
            like("delete from MeetingTranscriptSegment x where x.meetingId like :p escape '!'", "Đoạn transcript");
            like("delete from MtAttendanceEvent x where x.meetingId like :p escape '!'", "Sự kiện tham dự");
            like("delete from MeetingAuditLog x where x.meetingId like :p escape '!'", "Lịch sử cuộc họp");
            like("delete from MtLobbyRequest x where x.meetingId like :p escape '!'", "Yêu cầu phòng chờ");
            like("delete from MeetingMessage x where x.meetingId like :p escape '!'", "Tin nhắn");
            like("delete from MtWhiteboard x where x.meetingId like :p escape '!'", "Bảng trắng");
            like("delete from Notification x where x.id like :p escape '!' or x.meetingId like :p escape '!'", "Thông báo");
            like("delete from SyAuditLog x where x.id like :p escape '!' or x.targetId like :p escape '!'", "Nhật ký hoạt động");
            like("delete from MeetingPersonal x where x.meetingId like :p escape '!'", "Người tham gia");
            like("delete from MeetingInfo x where x.id like :p escape '!'", "Cuộc họp");
        });
        tx.executeWithoutResult(s -> {
            List<AdAccount> seeded = em.createQuery("select a from AdAccount a where a.createBy = :s", AdAccount.class)
                    .setParameter("s", SEED).getResultList();
            for (AdAccount a : seeded) {
                long refs = em.createQuery("select count(p) from MeetingPersonal p where p.userName = :u", Long.class)
                        .setParameter("u", a.getUserName()).getSingleResult()
                        + em.createQuery("select count(t) from MeetingTask t where t.createBy = :u or t.assigneeUserName = :u", Long.class)
                        .setParameter("u", a.getUserName()).getSingleResult();
                if (refs > 0) {
                    // Tài khoản đã được dùng ngoài dữ liệu demo → chỉ khoá, không xoá.
                    a.setActive(false);
                    inc("Tài khoản demo bị khoá (đang được dùng)");
                } else {
                    em.createQuery("delete from Notification n where n.recipientUserName = :u").setParameter("u", a.getUserName()).executeUpdate();
                    em.remove(a);
                    inc("Tài khoản demo");
                }
            }
            int unlocked = em.createQuery("update AdAccount a set a.active = true, a.updateBy = :s where a.updateBy = :lock")
                    .setParameter("s", SEED).setParameter("lock", SEED_LOCK).executeUpdate();
            counts.put("Tài khoản rác mở khoá lại", unlocked);
            int titles = em.createQuery("update MdTitle t set t.permissionJson = null where t.permissionJson = :j and t.updateBy = :s")
                    .setParameter("j", TITLE_PERMISSION_JSON).setParameter("s", SEED).executeUpdate();
            counts.put("Chức danh trả lại quyền cũ", titles);
        });
    }

    private void del(String jpql, String param, List<String> ids, String label) {
        counts.merge(label, em.createQuery(jpql).setParameter(param, ids).executeUpdate(), Integer::sum);
    }

    private void like(String jpql, String label) {
        counts.merge(label, em.createQuery(jpql).setParameter("p", LIKE_DEMO).executeUpdate(), Integer::sum);
    }

    // =====================================================================================
    // Tiện ích
    // =====================================================================================

    private void loadAccounts() {
        accounts = tx.execute(s -> em.createQuery("select a from AdAccount a", AdAccount.class).getResultList().stream()
                .collect(Collectors.toMap(a -> key(a.getUserName()), Function.identity(), (a, b) -> a, LinkedHashMap::new)));
    }

    private AdAccount account(String userName) {
        AdAccount a = accounts.get(key(userName));
        if (a == null) throw new IllegalStateException("Không có tài khoản " + userName);
        return a;
    }

    private String settingsJson(DemoData.Meeting m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("schemaVersion", 1);
        s.put("lobbyEnabled", m.lobby());
        s.put("allowGuests", m.allowGuests());
        s.put("allowJoinBeforeHost", false);
        s.put("chatEnabled", true);
        s.put("screenShareEnabled", true);
        s.put("whiteboardEnabled", true);
        s.put("fileUploadEnabled", true);
        s.put("recordingEnabled", true);
        s.put("captionsEnabled", true);
        s.put("aiMinutesEnabled", true);
        s.put("minutesTemplateId", "");
        s.put("passwordHash", null);
        s.put("hasPassword", false);
        return json(s);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String refId(PlannedMeeting p) {
        return DemoPlan.id("ref", p.source().key());
    }

    private static void stamp(vn.com.d2s.smr.entity.BaseEntity e, String user, LocalDateTime at) {
        e.setCreateBy(user);
        e.setCreateDate(at);
        e.setUpdateBy(user);
        e.setUpdateDate(at);
    }

    private void inc(String label) {
        counts.merge(label, 1, Integer::sum);
    }

    private static String key(String userName) {
        return userName == null ? "" : userName.toLowerCase();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
