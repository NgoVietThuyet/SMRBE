package vn.com.d2s.smr.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm tra nội dung demo-data.json và cách tính lịch, không cần DB. */
class DemoPlanTest {

    private static final DemoData DATA = DemoData.load(new ObjectMapper().findAndRegisterModules());
    /** 19/10/2026 03:00 UTC = 10:00 thứ Hai giờ VN. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 3, 0);

    @Test
    void plansAllMeetingsWithUniqueIdsAndRoomCodes() {
        List<DemoPlan.PlannedMeeting> plan = DemoPlan.plan(DATA, NOW);

        // 34 cuộc họp lẻ + 11 buổi giao ban (8 tuần trước, tuần này, 2 tuần sau).
        assertThat(plan).hasSize(DATA.meetings().size() + 11);
        assertThat(plan.stream().map(DemoPlan.PlannedMeeting::id).distinct()).hasSize(plan.size());
        Set<String> rooms = new HashSet<>();
        plan.forEach(p -> assertThat(rooms.add(DemoPlan.roomCode(p.source().key()))).isTrue());
        assertThat(rooms).allMatch(r -> r.length() == 8 && r.startsWith("d"));
        assertThat(plan).allMatch(p -> p.id().startsWith(DemoPlan.ID_PREFIX));
    }

    @Test
    void statusesMatchDates() {
        for (DemoPlan.PlannedMeeting p : DemoPlan.plan(DATA, NOW)) {
            switch (p.status()) {
                case ENDED, ARCHIVED -> assertThat(p.endUtc()).as(p.source().key()).isBefore(NOW);
                case SCHEDULED -> assertThat(p.startUtc()).as(p.source().key()).isAfter(NOW);
                case ONGOING -> assertThat(p.startUtc()).isBeforeOrEqualTo(NOW).satisfies(s -> assertThat(p.endUtc()).isAfter(NOW));
                default -> { }
            }
        }
    }

    @Test
    void recurringMeetingIsOnMondayMorningVietnamTime() {
        List<DemoPlan.PlannedMeeting> giaoban = DemoPlan.plan(DATA, NOW).stream()
                .filter(p -> p.source().key().startsWith("giaoban_")).toList();
        assertThat(giaoban).hasSize(11);
        assertThat(giaoban).allMatch(p -> p.localDate().getDayOfWeek() == DayOfWeek.MONDAY);
        // 08:30 giờ VN = 01:30 UTC.
        assertThat(giaoban).allMatch(p -> p.startUtc().getHour() == 1 && p.startUtc().getMinute() == 30);
        // Hôm nay 10:00 thứ Hai: buổi 08:30–09:30 hôm nay đã kết thúc.
        assertThat(giaoban.stream().filter(p -> p.localDate().equals(LocalDate.of(2026, 10, 19))).findFirst().orElseThrow().status())
                .isEqualTo(DemoPlan.Status.ENDED);
    }

    @Test
    void contentIsConsistent() {
        Set<String> users = new HashSet<>(List.of("khoa.tran", "linh.vu", "huong.le", "bao.nguyen", "hoang.tran", "vinh.vu", "loc.nguyen",
                "mai.dang", "nam.nguyen", "anh.dang", "thuyet.ngo", "thao.nguyen", "dat.pham", "anh.le", "ngoc.phan", "van.doan", "trung.bui",
                "manh.hoang", "kien.tran", "trang.hoang", "vy.phan", "nga.do", "huy.bui", "giang.le", "ha.duong", "danh.trinh", "phong.nguyen",
                "thao.vu", "hung.pham"));
        DATA.accounts().forEach(a -> users.add(a.userName()));
        for (DemoPlan.PlannedMeeting p : DemoPlan.plan(DATA, NOW)) {
            Map<String, Integer> members = p.members();
            assertThat(users).as(p.source().key()).containsAll(members.keySet());
            if (p.source().transcript() != null) {
                assertThat(DATA.transcripts()).containsKey(p.source().transcript());
                DATA.transcripts().get(p.source().transcript())
                        .forEach(l -> assertThat(members).as(p.source().key() + " / " + l.speaker()).containsKey(l.speaker()));
            }
        }
        DATA.boards().forEach(b -> b.lists().forEach(l -> l.cards().stream().filter(c -> c.assignee() != null)
                .forEach(c -> assertThat(users).as(b.key() + " / " + c.title()).contains(c.assignee()))));
        // Mỗi luồng biên bản AI phải có transcript; mẫu biên bản gắn với cuộc họp có thật.
        DATA.meetings().stream().filter(m -> m.minutesFlow() != null && !"manual".equals(m.minutesFlow()))
                .forEach(m -> assertThat(m.transcript()).as(m.key()).isNotNull());
        assertThat(DATA.meetings().stream().map(DemoData.Meeting::key)).containsAll(DATA.meetingTemplates().keySet());
    }

    @Test
    void fillsRelativeDatesInTranscript() {
        assertThat(DemoPlan.fillDates("hạn ngày {+7}, bản Beta trước ngày {+45}.", LocalDate.of(2026, 9, 9)))
                .isEqualTo("hạn ngày 16/09, bản Beta trước ngày 24/10.");
    }

    @Test
    void generatedFilesAndTemplatesAreReadable() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(DemoFiles.create("Bao_cao.docx", "Họp A", "Công ty").bytes()))) {
            assertThat(doc.getParagraphs().get(0).getText()).isEqualTo("BAO CAO");
        }
        assertThat(DemoFiles.create("Bang.xlsx", "x", "c").bytes()).isNotEmpty();
        assertThat(DemoFiles.create("Anh.png", "x", "c").bytes()).isNotEmpty();
        // Hai mẫu biên bản demo phải đọc được bằng chính bộ đọc mẫu của hệ thống.
        assertThat(MinutesTemplateReader.read("mau.docx", DemoFiles.docxTemplate("Công ty")).sectionTitles())
                .contains("Thời gian", "I. Nội dung cuộc họp", "II. Kết luận của chủ trì", "III. Phân công thực hiện");
        assertThat(MinutesTemplateReader.read("mau.pdf", DemoFiles.pdfTemplate("Công ty")).sections()).hasSizeGreaterThanOrEqualTo(4);
    }
}
