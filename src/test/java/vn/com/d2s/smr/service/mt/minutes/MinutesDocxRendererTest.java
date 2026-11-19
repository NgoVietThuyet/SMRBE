package vn.com.d2s.smr.service.mt.minutes;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MinutesDocxRendererTest {

    @Test
    void defaultLayoutUsesTimesNewRoman13AndActionTable() throws Exception {
        Map<String, String> v = Map.of("{{meeting_name}}", "Họp A", "{{time}}", "t", "{{participants}}", "p",
                "{{summary}}", "Tóm tắt", "{{decisions}}", "1. x", "{{action_items}}", "txt");
        byte[] bytes = MinutesDocxRenderer.render(null, v, List.<String[]>of(new String[]{"1", "Làm việc", "An", "20/10/2026"}));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(doc.getTables()).hasSize(1);
            var t = doc.getTables().get(0);
            assertThat(t.getRow(0).getCell(0).getText()).isEqualTo("STT");
            assertThat(t.getRow(0).getCell(3).getText()).isEqualTo("Deadline");
            assertThat(t.getRow(1).getCell(1).getText()).isEqualTo("Làm việc");
            assertThat(t.getRow(1).getCell(3).getText()).isEqualTo("20/10/2026");
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    assertThat(r.getFontFamily()).isEqualTo("Times New Roman");
                    assertThat(r.getFontSize()).isEqualTo(13);
                }
            }
            assertThat(doc.getParagraphs().stream().map(XWPFParagraph::getText)).noneMatch(s -> s.contains("{{"));
        }
    }

    @Test
    void professionalLayoutHasLetterheadTablesAndSignatures() throws Exception {
        var model = new MinutesDocModel("Công ty cổ phần phần mềm ABS", true, "Họp BTL", "22:01 13/11/2026",
                "Ngô Việt Thuyết", "Lý Đức Tú",
                List.of(new MinutesDocModel.Person("Ngô Việt Thuyết", "Chủ trì"), new MinutesDocModel.Person("Lý Đức Tú", "Thư ký")),
                List.of("Đoạn một.", "Đoạn hai."), List.of(new MinutesDocModel.Discussion("Giao diện", "Chốt dùng Expo.")),
                List.of("Ra mắt thứ Hai"), List.of(new MinutesDocModel.Action("Làm việc", "An", "Chưa xác định")), "Biên bản đã duyệt (phiên bản 1).");
        byte[] bytes = MinutesDocxRenderer.renderDefault(model);
        java.nio.file.Files.write(java.nio.file.Path.of("target", "sample-minutes.docx"), bytes);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            // đầu trang, thông tin, thành phần, việc cần làm, chữ ký
            assertThat(doc.getTables()).hasSize(5);
            assertThat(doc.getTables().get(0).getRow(0).getCell(0).getText()).startsWith("CÔNG TY CỔ PHẦN PHẦN MỀM ABS");
            assertThat(doc.getTables().get(0).getRow(0).getCell(1).getText()).contains("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM").contains("Độc lập – Tự do – Hạnh phúc");
            assertThat(doc.getTables().get(2).getRows()).hasSize(3);
            assertThat(doc.getTables().get(3).getRow(0).getCell(3).getText()).isEqualTo("Deadline");
            assertThat(doc.getTables().get(4).getRow(0).getCell(0).getText()).contains("THƯ KÝ").contains("Lý Đức Tú");
            assertThat(doc.getTables().get(4).getRow(0).getCell(1).getText()).contains("CHỦ TRÌ").contains("Ngô Việt Thuyết");
            String text = doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("", (a, b) -> a + "\n" + b);
            assertThat(text).contains("BIÊN BẢN CUỘC HỌP").contains("III. KẾT LUẬN VÀ QUYẾT ĐỊNH").contains("1. Giao diện: Chốt dùng Expo.");
            assertThat(doc.getFooterList().get(0).getText()).contains("Công ty cổ phần phần mềm ABS");
        }
    }

    @Test
    void fileNameIsMeetingName() {
        assertThat(MinutesExportServiceImpl.fileName("Họp: giao ban/tuần?")).isEqualTo("Biên bản Họp giao ban tuần.docx");
        assertThat(MinutesExportServiceImpl.fileName("  ")).endsWith(".docx");
    }
}
