package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.dto.mt.minutes.MinutesExportResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesTemplateInfo;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinutesExportServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");
    private static final LocalDateTime T0 = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final String MID = "m1";

    @Mock private MeetingInfoRepository meetingRepository;
    @Mock private MeetingPersonalRepository personalRepository;
    @Mock private MeetingTranscriptSegmentRepository segmentRepository;
    @Mock private MtMinutesRepository minutesRepository;
    @Mock private MeetingTaskRepository taskRepository;
    @Mock private SyBackgroundJobRepository jobRepository;
    @Mock private CmFileRepository fileRepository;
    @Mock private AdAccountRepository accountRepository;
    @Mock private ObjectStorageService storage;
    @Mock private LlmClientService llm;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, byte[]> objects = new HashMap<>();
    private final Map<String, CmFile> files = new HashMap<>();
    private final Map<String, SyBackgroundJob> jobs = new HashMap<>();
    private MinutesExportServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MinutesExportServiceImpl(meetingRepository, personalRepository, segmentRepository, minutesRepository,
                taskRepository, jobRepository, fileRepository, accountRepository, storage, llm, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
        service.runJobsInline = true;

        MeetingInfo m = new MeetingInfo();
        m.setId(MID);
        m.setName("Họp giao ban");
        m.setRoomCode("ABC123");
        m.setStatus(3);
        m.setStartDate(T0.minusHours(1));
        m.setEndDate(T0);
        when(meetingRepository.findByIdAndDeletedFalse(MID)).thenReturn(Optional.of(m));
        when(meetingRepository.findById(MID)).thenReturn(Optional.of(m));
        when(personalRepository.findByMeetingId(MID)).thenReturn(List.of(
                person("host", "Chủ Trì", 1), person("sec", "Thư Ký", 3), person("mem", "Thành Viên", 5)));
        MeetingTranscriptSegment seg = new MeetingTranscriptSegment();
        seg.setId("s1");
        seg.setMeetingId(MID);
        seg.setSpeakerName("Chủ Trì");
        seg.setText("Thống nhất ra mắt vào thứ Hai.");
        seg.setSpokenAt(T0.minusMinutes(50));
        when(segmentRepository.findByMeetingIdOrderBySpokenAtAsc(MID)).thenReturn(List.of(seg));
        when(taskRepository.findByMeetingId(MID)).thenReturn(List.of());
        when(accountRepository.findById(anyString())).thenReturn(Optional.empty());

        doAnswer(i -> {
            objects.put(i.getArgument(0), ((InputStream) i.getArgument(1)).readAllBytes());
            return null;
        }).when(storage).upload(anyString(), any(InputStream.class), anyLong(), anyString());
        when(storage.download(anyString())).thenAnswer(i -> new ByteArrayInputStream(objects.get((String) i.getArgument(0))));
        doAnswer(i -> objects.remove((String) i.getArgument(0))).when(storage).delete(anyString());

        when(fileRepository.save(any(CmFile.class))).thenAnswer(i -> {
            CmFile f = i.getArgument(0);
            files.put(f.getId(), f);
            return f;
        });
        doAnswer(i -> files.remove(((CmFile) i.getArgument(0)).getId())).when(fileRepository).delete(any(CmFile.class));
        when(fileRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(files.get((String) i.getArgument(0))));
        when(fileRepository.findByTypeAndReferenceFileIdOrderByCreateDateDesc(anyInt(), anyString())).thenAnswer(i -> files.values().stream()
                .filter(f -> f.getType() == (int) i.getArgument(0) && f.getReferenceFileId().equals(i.getArgument(1)))
                .sorted(Comparator.comparing(CmFile::getCreateDate).reversed()).toList());

        when(jobRepository.saveAndFlush(any(SyBackgroundJob.class))).thenAnswer(i -> put(i.getArgument(0)));
        when(jobRepository.save(any(SyBackgroundJob.class))).thenAnswer(i -> put(i.getArgument(0)));
        when(jobRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(jobs.get((String) i.getArgument(0))));
        when(jobRepository.findByIdempotencyKey(anyString())).thenAnswer(i -> jobs.values().stream()
                .filter(j -> j.getIdempotencyKey().equals(i.getArgument(0))).findFirst());
        when(jobRepository.countByTypeAndRefIdAndCreatedByAndCreatedAtAfter(anyString(), anyString(), anyString(), any()))
                .thenAnswer(i -> jobs.values().stream().filter(j -> j.getCreatedBy().equals(i.getArgument(2))).count());

        // Mặc định cuộc họp đã có biên bản đã duyệt: file Word chỉ đóng gói bản này, không gọi AI.
        MtMinutes approved = new MtMinutes();
        approved.setId("minA");
        approved.setVersion(1);
        approved.setStatus(MtMinutes.APPROVED);
        approved.setContentJson(mapper.writeValueAsString(new MinutesContent("Họp giao ban tuần.", List.of(),
                List.of(new MinutesContent.Decision("d1", "Ra mắt thứ Hai", List.of("s1"))), List.of())));
        when(minutesRepository.findByMeetingIdOrderByVersionDesc(MID)).thenReturn(List.of(approved));

        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt())).thenAnswer(i -> {
            String user = i.getArgument(1);
            // Mẫu tự do: trả nội dung cho mọi mục AI được hỏi.
            if (user.startsWith("DANH SÁCH MỤC")) {
                StringBuilder sb = new StringBuilder("{\"sections\":[");
                for (String line : user.split("\n")) {
                    if (line.startsWith("- M")) {
                        String id = line.substring(2, line.indexOf(':'));
                        sb.append("{\"id\":\"").append(id).append("\",\"content\":\"AI viết cho ").append(id).append("\"},");
                    }
                }
                return sb.substring(0, sb.length() - 1) + "]}";
            }
            return "{\"summary\":\"Họp giao ban tuần.\",\"decisions\":[{\"text\":\"Ra mắt thứ Hai\",\"sources\":[\"S1\"]}],\"action_items\":[]}";
        });
    }

    private SyBackgroundJob put(SyBackgroundJob j) {
        jobs.put(j.getId(), j);
        return j;
    }

    private static MeetingPersonal person(String user, String name, int type) {
        MeetingPersonal p = new MeetingPersonal();
        p.setMeetingId(MID);
        p.setUserName(user);
        p.setFullName(name);
        p.setType(type);
        p.setChairperson(type == 1);
        return p;
    }

    private MockMultipartFile docx(String name) throws Exception {
        return new MockMultipartFile("file", name, "application/octet-stream", MinutesTemplateTest.sampleDocx());
    }

    private static String text(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return String.join("\n", doc.getParagraphs().stream().map(XWPFParagraph::getText).toList());
        }
    }

    @Test
    void anyMemberUploadsTemplateWithoutRunningAiAndNewOneReplacesOld() throws Exception {
        MinutesTemplateInfo first = service.uploadTemplate("mem", MID, docx("mau-1.docx"));
        MinutesTemplateInfo second = service.uploadTemplate("sec", MID, docx("mau-2.docx"));

        assertThat(first.source()).isEqualTo("MEETING");
        assertThat(first.sections()).contains("I. Nội dung thảo luận");
        verify(llm, never()).generate(anyString(), anyString(), anyDouble(), anyInt());
        // Chỉ còn một mẫu: mẫu tải sau.
        assertThat(files.values()).extracting(CmFile::getFileName).containsExactly("mau-2.docx");
        assertThat(service.template("host", MID).fileId()).isEqualTo(second.fileId());
        assertThat(service.template("mem", MID).canDelete()).isFalse();
        assertThat(service.template("host", MID).canDelete()).isTrue();
    }

    @Test
    void outsiderAndGuestCannotUploadOrExport() throws Exception {
        assertThatThrownBy(() -> service.uploadTemplate("stranger", MID, docx("x.docx"))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.requestExport("guest_1", MID)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.uploadTemplate("mem", MID,
                new MockMultipartFile("file", "x.txt", "text/plain", "abc".getBytes()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportFillsUploadedTemplateFromMeetingData() throws Exception {
        service.uploadTemplate("mem", MID, docx("mau.docx"));

        MinutesExportResponse r = service.requestExport("mem", MID);

        assertThat(jobs.get(r.id()).getStatus()).isEqualTo(SyBackgroundJob.DONE);
        String text = text(service.download("mem", r.id()).content());
        assertThat(text).contains("BIÊN BẢN HỌP GIAO BAN")
                .contains("Chủ trì: Chủ Trì")
                .contains("Thành phần tham dự: Chủ Trì (Chủ trì); Thư Ký (Thư ký); Thành Viên")
                .contains("Địa điểm: Họp trực tuyến trên hệ thống Smart Meeting (mã phòng ABC123)")
                .contains("Họp giao ban tuần.")
                .contains("đã duyệt (phiên bản 1)")
                .doesNotContain("AI viết cho");
        verify(llm, never()).generate(anyString(), anyString(), anyDouble(), anyInt());
        // Người ngoài cuộc họp không tải được file.
        assertThatThrownBy(() -> service.download("stranger", r.id())).isInstanceOf(SecurityException.class);
    }

    @Test
    void publishedMinutesProvideDecisionsAndActions() throws Exception {
        MtMinutes pub = new MtMinutes();
        pub.setId("min1");
        pub.setVersion(2);
        pub.setStatus(MtMinutes.PUBLISHED);
        pub.setContentJson(mapper.writeValueAsString(new MinutesContent("Tóm tắt", List.of(),
                List.of(new MinutesContent.Decision("d1", "Quyết định đã phát hành", List.of("s1"))),
                List.of(new MinutesContent.ActionItem("a1", "Gửi báo cáo", "mem", "Thành Viên", "2026-10-20", List.of("s1"), true, "t1")))));
        when(minutesRepository.findByMeetingIdOrderByVersionDesc(MID)).thenReturn(List.of(pub));
        service.uploadTemplate("mem", MID, docx("mau.docx"));

        String text = text(service.download("mem", service.requestExport("mem", MID).id()).content());

        assertThat(text).contains("1. Quyết định đã phát hành")
                .contains("1. Gửi báo cáo — Phụ trách: Thành Viên — Hạn: 20/10/2026")
                .contains("phiên bản 2");
        verify(llm, never()).generate(anyString(), anyString(), anyDouble(), anyInt());
    }

    @Test
    void repeatedRequestReusesFileUntilDataChanges() throws Exception {
        MinutesExportResponse first = service.requestExport("mem", MID);
        MinutesExportResponse again = service.requestExport("sec", MID);

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.reused()).isTrue();
        verify(llm, never()).generate(anyString(), anyString(), anyDouble(), anyInt());

        // Tải mẫu mới → dữ liệu khác → job mới.
        service.uploadTemplate("mem", MID, docx("mau.docx"));
        assertThat(service.requestExport("mem", MID).id()).isNotEqualTo(first.id());
    }

    @Test
    void noTemplateUsesBuiltinLayout() throws Exception {
        MinutesExportResponse r = service.requestExport("mem", MID);
        String text = text(service.download("mem", r.id()).content());
        assertThat(text).contains("BIÊN BẢN CUỘC HỌP").contains("Họp giao ban tuần.").contains("1. Ra mắt thứ Hai");
        assertThat(service.template("mem", MID).source()).isEqualTo("BUILTIN");
    }

    @Test
    void exportRequiresApprovedOrPublishedMinutes() throws Exception {
        MtMinutes draft = new MtMinutes();
        draft.setId("minD");
        draft.setVersion(2);
        draft.setStatus(MtMinutes.DRAFT);
        draft.setContentJson("{}");
        when(minutesRepository.findByMeetingIdOrderByVersionDesc(MID)).thenReturn(List.of(draft));

        assertThatThrownBy(() -> service.requestExport("mem", MID)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chưa có biên bản đã duyệt");
        verify(llm, never()).generate(anyString(), anyString(), anyDouble(), anyInt());
    }

    @Test
    void limitsRequestsPerHour() throws Exception {
        // Đã có 5 job trong giờ qua → từ chối.
        for (int i = 0; i < 5; i++) {
            SyBackgroundJob j = new SyBackgroundJob();
            j.setId("x" + i);
            j.setIdempotencyKey("k" + i);
            j.setCreatedBy("mem");
            jobs.put(j.getId(), j);
        }
        assertThatThrownBy(() -> service.requestExport("mem", MID)).isInstanceOf(IllegalStateException.class).hasMessageContaining("5 lần");
        verify(jobRepository, never()).countByTypeAndRefIdAndCreatedByAndCreatedAtAfter(eq("OTHER"), anyString(), anyString(), any());
    }
}
