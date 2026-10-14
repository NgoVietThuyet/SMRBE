package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.com.d2s.smr.config.LlmProperties;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.dto.mt.minutes.MinutesJobResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesVersionResponse;
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
import vn.com.d2s.smr.service.mt.LlmClientService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinutesServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
    private static final LocalDateTime T0 = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String MID = "m1";

    @Mock private MeetingInfoRepository meetingRepository;
    @Mock private MeetingPersonalRepository personalRepository;
    @Mock private MeetingTranscriptSegmentRepository segmentRepository;
    @Mock private MtMinutesRepository minutesRepository;
    @Mock private SyBackgroundJobRepository jobRepository;
    @Mock private MeetingTaskRepository taskRepository;
    @Mock private AdAccountRepository accountRepository;
    @Mock private LlmClientService llm;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final List<MtMinutes> minutes = new ArrayList<>();
    private final Map<String, SyBackgroundJob> jobs = new HashMap<>();
    private final List<MeetingTask> tasks = new ArrayList<>();
    private final List<MeetingTranscriptSegment> segments = new ArrayList<>();
    private MinutesServiceImpl service;

    @BeforeEach
    void setUp() {
        LlmProperties props = new LlmProperties("http://x", "k", "default", true, null, 1000, 1000);
        service = new MinutesServiceImpl(meetingRepository, personalRepository, segmentRepository, minutesRepository,
                jobRepository, taskRepository, accountRepository, llm, props, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        service.runJobsInline = true;

        MeetingInfo m = new MeetingInfo();
        m.setId(MID);
        m.setName("Họp giao ban");
        m.setRoomCode("ABC123");
        m.setStatus(3);
        m.setStartDate(T0.minusHours(1));
        when(meetingRepository.findByIdAndDeletedFalse(MID)).thenReturn(Optional.of(m));
        when(meetingRepository.findById(MID)).thenReturn(Optional.of(m));
        when(personalRepository.findByMeetingId(MID)).thenReturn(List.of(
                person("host", "Chủ Trì", 1, true), person("sec", "Thư Ký", 3, false), person("mem", "Thành Viên", 5, false)));

        segments.add(seg("s1", "Chủ Trì", "Thống nhất ra mắt vào thứ Hai.", T0.minusMinutes(50)));
        segments.add(seg("s2", "Thư Ký", "Anh mem chuẩn bị tài liệu trước 20/10.", T0.minusMinutes(45)));
        when(segmentRepository.findByMeetingIdOrderBySpokenAtAsc(MID)).thenAnswer(i -> List.copyOf(segments));

        when(minutesRepository.save(any(MtMinutes.class))).thenAnswer(i -> {
            MtMinutes x = i.getArgument(0);
            minutes.removeIf(y -> y.getId().equals(x.getId()));
            minutes.add(x);
            return x;
        });
        when(minutesRepository.findByMeetingIdOrderByVersionDesc(MID)).thenAnswer(i -> minutes.stream()
                .sorted(Comparator.comparingInt(MtMinutes::getVersion).reversed()).toList());
        when(minutesRepository.findFirstByMeetingIdOrderByVersionDesc(MID)).thenAnswer(i -> minutes.stream()
                .max(Comparator.comparingInt(MtMinutes::getVersion)));
        when(minutesRepository.findByMeetingIdAndVersion(anyString(), anyInt())).thenAnswer(i -> minutes.stream()
                .filter(x -> x.getVersion() == (int) i.getArgument(1)).findFirst());

        when(jobRepository.saveAndFlush(any(SyBackgroundJob.class))).thenAnswer(i -> put(i.getArgument(0)));
        when(jobRepository.save(any(SyBackgroundJob.class))).thenAnswer(i -> put(i.getArgument(0)));
        when(jobRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(jobs.get((String) i.getArgument(0))));
        when(jobRepository.findByIdempotencyKey(anyString())).thenAnswer(i -> jobs.values().stream()
                .filter(j -> j.getIdempotencyKey().equals(i.getArgument(0))).findFirst());

        when(taskRepository.save(any(MeetingTask.class))).thenAnswer(i -> {
            MeetingTask t = i.getArgument(0);
            tasks.removeIf(y -> y.getId().equals(t.getId()));
            tasks.add(t);
            return t;
        });
        when(taskRepository.findByMeetingId(MID)).thenAnswer(i -> List.copyOf(tasks));
        when(taskRepository.findByParentId(anyString())).thenAnswer(i -> tasks.stream()
                .filter(t -> i.getArgument(0).equals(t.getParentId())).toList());
        when(taskRepository.maxSortOrder(anyString())).thenReturn(-1);

        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("""
                Đây là biên bản: {"summary":"Họp giao ban tuần.","summary_sources":["S1"],
                "decisions":[{"text":"Ra mắt vào thứ Hai","sources":["S1","S99"]}],
                "action_items":[{"text":"Chuẩn bị tài liệu","assignee":"mem","due_date":"2026-10-20","sources":["S2"]}]}
                """);
    }

    private SyBackgroundJob put(SyBackgroundJob j) {
        jobs.put(j.getId(), j);
        return j;
    }

    private static MeetingPersonal person(String user, String name, int type, boolean chair) {
        MeetingPersonal p = new MeetingPersonal();
        p.setMeetingId(MID);
        p.setUserName(user);
        p.setFullName(name);
        p.setType(type);
        p.setChairperson(chair);
        return p;
    }

    private static MeetingTranscriptSegment seg(String id, String speaker, String text, LocalDateTime at) {
        MeetingTranscriptSegment s = new MeetingTranscriptSegment();
        s.setId(id);
        s.setMeetingId(MID);
        s.setSpeakerName(speaker);
        s.setText(text);
        s.setSpokenAt(at);
        return s;
    }

    @Test
    void generateCreatesAiDraftWithMappedSources() {
        MinutesJobResponse job = service.generate("sec", MID);

        assertThat(jobs.get(job.id()).getStatus()).isEqualTo(SyBackgroundJob.DONE);
        assertThat(minutes).hasSize(1);
        MinutesVersionResponse v = service.detail("sec", MID, null).current();
        assertThat(v.version()).isEqualTo(1);
        assertThat(v.status()).isEqualTo(MtMinutes.DRAFT);
        assertThat(v.source()).isEqualTo("AI");
        assertThat(v.content().decisions().get(0).sources()).containsExactly("s1");
        MinutesContent.ActionItem a = v.content().actions().get(0);
        assertThat(a.assignee()).isEqualTo("mem");
        assertThat(a.assigneeName()).isEqualTo("Thành Viên");
        assertThat(a.dueDate()).isEqualTo("2026-10-20");
        assertThat(a.sources()).containsExactly("s2");
        assertThat(v.warnings()).isEmpty();
    }

    @Test
    void sameTranscriptReusesJob() {
        MinutesJobResponse first = service.generate("sec", MID);
        MinutesJobResponse second = service.generate("host", MID);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.reused()).isTrue();
        verify(llm, times(1)).generate(anyString(), anyString(), anyDouble(), anyInt());

        segments.add(seg("s3", "Chủ Trì", "Bổ sung.", T0.minusMinutes(40)));
        MinutesJobResponse third = service.generate("sec", MID);
        assertThat(third.id()).isNotEqualTo(first.id());
        assertThat(minutes).hasSize(2);
    }

    @Test
    void failedLlmMarksJobFailedAndCanRetry() {
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn(null);
        MinutesJobResponse job = service.generate("sec", MID);
        assertThat(jobs.get(job.id()).getStatus()).isEqualTo(SyBackgroundJob.FAILED);
        assertThat(minutes).isEmpty();

        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("{\"summary\":\"ok\"}");
        MinutesJobResponse retry = service.generate("sec", MID);
        assertThat(retry.reused()).isFalse();
        assertThat(jobs.get(retry.id()).getStatus()).isEqualTo(SyBackgroundJob.DONE);
    }

    @Test
    void ordinaryMemberCannotGenerateOrSeeDrafts() {
        assertThatThrownBy(() -> service.generate("mem", MID)).isInstanceOf(SecurityException.class);
        service.generate("sec", MID);
        assertThat(service.detail("mem", MID, null).current()).isNull();
        assertThatThrownBy(() -> service.detail("outsider", MID, null)).isInstanceOf(SecurityException.class);
    }

    @Test
    void workflowPublishCreatesTasksAndLocksVersion() {
        service.generate("sec", MID);
        service.submit("sec", MID, 1);
        assertThatThrownBy(() -> service.approve("sec", MID, 1)).isInstanceOf(SecurityException.class);
        service.approve("host", MID, 1);
        MinutesVersionResponse pub = service.publish("host", MID, 1);

        assertThat(pub.status()).isEqualTo(MtMinutes.PUBLISHED);
        assertThat(pub.content().actions().get(0).taskId()).isNotNull();
        assertThat(tasks).extracting(MeetingTask::getLevel).containsExactlyInAnyOrder(0, 1, 2);
        MeetingTask card = tasks.stream().filter(t -> t.getLevel() == 2).findFirst().orElseThrow();
        assertThat(card.getSourceRef()).startsWith("minutes:");
        // 17:00 giờ VN = 10:00 UTC
        assertThat(card.getDueDate()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));

        // Bản đã phát hành bất biến; thành viên thường thấy được.
        assertThatThrownBy(() -> service.update("host", MID, 1, MinutesContent.empty())).isInstanceOf(IllegalStateException.class);
        assertThat(service.detail("mem", MID, null).current().version()).isEqualTo(1);

        // Bản nháp mới chép nội dung, phát hành lại không tạo trùng công việc.
        MinutesVersionResponse v2 = service.newDraft("sec", MID);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.content().actions().get(0).taskId()).isEqualTo(pub.content().actions().get(0).taskId());
    }

    @Test
    void publishRejectedWhenItemHasNoSource() {
        service.generate("sec", MID);
        MinutesContent c = service.detail("sec", MID, 1).current().content();
        List<MinutesContent.Decision> decisions = new ArrayList<>(c.decisions());
        decisions.add(new MinutesContent.Decision(null, "Quyết định không nguồn", List.of("fake-id")));
        MinutesVersionResponse updated = service.update("sec", MID, 1,
                new MinutesContent(c.summary(), c.summarySources(), decisions, c.actions()));
        assertThat(updated.warnings()).hasSize(1);

        service.submit("sec", MID, 1);
        service.approve("host", MID, 1);
        assertThatThrownBy(() -> service.publish("host", MID, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Quyết định 2");
    }

    @Test
    void rejectRequiresReasonAndReturnsToEditable() {
        service.generate("sec", MID);
        service.submit("sec", MID, 1);
        assertThatThrownBy(() -> service.reject("host", MID, 1, " ")).isInstanceOf(IllegalArgumentException.class);
        MinutesVersionResponse r = service.reject("host", MID, 1, "Thiếu nội dung");
        assertThat(r.status()).isEqualTo(MtMinutes.REJECTED);
        MinutesVersionResponse u = service.update("sec", MID, 1, r.content());
        assertThat(u.status()).isEqualTo(MtMinutes.DRAFT);
        assertThat(u.rejectReason()).isNull();
    }

    @Test
    void exportDocxUsesDefaultTemplate() throws Exception {
        service.generate("sec", MID);
        assertThatThrownBy(() -> service.exportDocx("mem", MID, 1)).isInstanceOf(SecurityException.class);
        MinutesService.ExportedDocx file = service.exportDocx("sec", MID, 1);
        assertThat(file.fileName()).startsWith("BienBan_ABC123_").endsWith("_v1.docx");
        try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(file.content()))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertThat(text).contains("Họp giao ban").contains("Ra mắt vào thứ Hai").contains("Thành Viên").doesNotContain("{{");
        }
    }
}
