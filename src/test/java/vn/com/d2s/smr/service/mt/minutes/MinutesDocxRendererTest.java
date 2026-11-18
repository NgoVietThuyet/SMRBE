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
    void fileNameIsMeetingName() {
        assertThat(MinutesExportServiceImpl.fileName("Họp: giao ban/tuần?")).isEqualTo("Biên bản Họp giao ban tuần.docx");
        assertThat(MinutesExportServiceImpl.fileName("  ")).endsWith(".docx");
    }
}
