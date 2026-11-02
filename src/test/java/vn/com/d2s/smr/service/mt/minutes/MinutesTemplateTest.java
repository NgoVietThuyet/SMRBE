package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Kind;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Section;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Đọc mẫu biên bản (.docx / .pdf), đọc kết quả AI và điền vào mẫu. */
class MinutesTemplateTest {

    /** Mẫu kiểu đơn vị hành chính: quốc hiệu, tiêu đề, dòng "Nhãn: ……", mục in đậm đánh số; không có placeholder. */
    static byte[] sampleDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            bold(doc, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM");
            bold(doc, "BIÊN BẢN HỌP GIAO BAN");
            plain(doc, "Thời gian: ……………………");
            plain(doc, "Địa điểm: ……………………");
            plain(doc, "Chủ trì: ……………………");
            plain(doc, "Thành phần tham dự: ……………………");
            bold(doc, "I. Nội dung thảo luận");
            plain(doc, "(Ghi tóm tắt các ý kiến phát biểu)");
            plain(doc, "…………………………………………");
            bold(doc, "II. Kết luận của chủ trì");
            plain(doc, "…………………………………………");
            bold(doc, "III. Phân công nhiệm vụ");
            plain(doc, "…………………………………………");
            plain(doc, "Nơi nhận: ……………………");
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void bold(XWPFDocument doc, String text) {
        XWPFRun r = doc.createParagraph().createRun();
        r.setBold(true);
        r.setText(text);
    }

    private static void plain(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    private static Section byTitle(Template t, String title) {
        return t.sections().stream().filter(s -> s.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void readsDocxSectionsAndClassifiesThem() throws Exception {
        Template t = MinutesTemplateReader.read("mau.docx", sampleDocx());

        assertThat(t.placeholderBased()).isFalse();
        assertThat(t.documentTitle()).isEqualTo("BIÊN BẢN HỌP GIAO BAN");
        assertThat(t.sectionTitles()).containsExactly("Thời gian", "Địa điểm", "Chủ trì", "Thành phần tham dự",
                "I. Nội dung thảo luận", "II. Kết luận của chủ trì", "III. Phân công nhiệm vụ", "Nơi nhận");
        assertThat(byTitle(t, "Thời gian").kind()).isEqualTo(Kind.TIME);
        assertThat(byTitle(t, "Thời gian").inline()).isTrue();
        assertThat(byTitle(t, "Địa điểm").kind()).isEqualTo(Kind.PLACE);
        assertThat(byTitle(t, "Chủ trì").kind()).isEqualTo(Kind.HOST);
        assertThat(byTitle(t, "Thành phần tham dự").kind()).isEqualTo(Kind.PARTICIPANTS);
        assertThat(byTitle(t, "I. Nội dung thảo luận").kind()).isEqualTo(Kind.AI);
        assertThat(byTitle(t, "I. Nội dung thảo luận").hint()).contains("Ghi tóm tắt");
        assertThat(byTitle(t, "II. Kết luận của chủ trì").kind()).isEqualTo(Kind.DECISIONS);
        assertThat(byTitle(t, "III. Phân công nhiệm vụ").kind()).isEqualTo(Kind.ACTIONS);
        assertThat(byTitle(t, "Nơi nhận").kind()).isEqualTo(Kind.AI);
    }

    @Test
    void placeholderTemplateIsNotSplitIntoSections() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            bold(doc, "BIÊN BẢN");
            plain(doc, "Cuộc họp: {{meeting_name}}");
            plain(doc, "{{summary}}");
            doc.write(out);
            Template t = MinutesTemplateReader.read("p7.docx", out.toByteArray());
            assertThat(t.placeholderBased()).isTrue();
            assertThat(t.sections()).isEmpty();
        }
    }

    @Test
    void readsPdfSections() throws Exception {
        byte[] pdf = pdf(List.of("BIEN BAN HOP", "Thoi gian: ..........", "Chu tri: ..........",
                "I. Noi dung thao luan", "(ghi tom tat)", "II. Ket luan", "III. Phan cong nhiem vu"));

        Template t = MinutesTemplateReader.read("mau.pdf", pdf);

        assertThat(t.format()).isEqualTo(MinutesTemplateReader.Format.PDF);
        assertThat(t.documentTitle()).isEqualTo("BIEN BAN HOP");
        assertThat(t.sectionTitles()).containsExactly("Thoi gian", "Chu tri", "I. Noi dung thao luan", "II. Ket luan", "III. Phan cong nhiem vu");
        assertThat(t.sections().get(1).kind()).isEqualTo(Kind.HOST);
        assertThat(t.sections().get(3).kind()).isEqualTo(Kind.DECISIONS);
    }

    static byte[] pdf(List<String> lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                float y = 740;
                for (String line : lines) {
                    cs.beginText();
                    cs.newLineAtOffset(60, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 24;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void rejectsUnsupportedOrUnreadableFiles() throws Exception {
        assertThatThrownBy(() -> MinutesTemplateReader.read("mau.doc", new byte[]{1, 2, 3, 4, 5}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".doc");
        // Đổi đuôi file: nội dung không phải docx.
        assertThatThrownBy(() -> MinutesTemplateReader.read("mau.docx", "khong phai docx".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        // PDF không có chữ (ảnh scan).
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            assertThatThrownBy(() -> MinutesTemplateReader.read("scan.pdf", out.toByteArray()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scan");
        }
        // Văn bản không có mục nào.
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            plain(doc, "Đây chỉ là một đoạn văn bình thường, không có tiêu đề hay nhãn nào để điền nội dung vào cả.");
            doc.write(out);
            assertThatThrownBy(() -> MinutesTemplateReader.read("trong.docx", out.toByteArray()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Không nhận ra mục");
        }
    }

    @Test
    void parseFillsMissingSectionsWithNoInfo() throws Exception {
        Template t = MinutesTemplateReader.read("mau.docx", sampleDocx());
        List<Section> ai = t.sections().stream().filter(s -> s.kind() == Kind.AI).toList();
        String raw = "Kết quả: {\"sections\":[{\"id\":\"" + ai.get(0).id() + "\",\"content\":\"Các bên **thống nhất** lịch ra mắt.\"},"
                + "{\"id\":\"M99\",\"content\":\"mục lạ\"}]}";

        Map<String, String> out = MinutesTemplateGenerator.parse(raw, ai, new ObjectMapper());

        assertThat(out).hasSize(2);
        assertThat(out.get(ai.get(0).id())).isEqualTo("Các bên thống nhất lịch ra mắt.");
        assertThat(out.get(ai.get(1).id())).isEqualTo(MinutesTemplateRenderer.NO_INFO);
        assertThatThrownBy(() -> MinutesTemplateGenerator.parse("không phải json", ai, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fillDocxKeepsLayoutAndReplacesPlaceholdersText() throws Exception {
        byte[] template = sampleDocx();
        Template t = MinutesTemplateReader.read("mau.docx", template);
        Map<String, String> content = t.sections().stream().collect(Collectors.toMap(Section::id, s -> switch (s.kind()) {
            case TIME -> "09:00 14/10/2026";
            case HOST -> "Nguyễn Văn A";
            case DECISIONS -> "1. Ra mắt vào thứ Hai\n2. Tăng ngân sách";
            case AI -> s.title().startsWith("I.") ? "Thảo luận tiến độ dự án." : "";
            default -> "x";
        }));

        byte[] result = MinutesTemplateRenderer.fillDocx(template, t, content, "Biên bản do AI tổng hợp, chưa qua duyệt.");

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            List<String> lines = doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();
            String text = String.join("\n", lines);
            // Giữ quốc hiệu, tiêu đề và các tiêu đề mục của mẫu.
            assertThat(lines.get(0)).isEqualTo("CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM");
            assertThat(lines).contains("BIÊN BẢN HỌP GIAO BAN", "I. Nội dung thảo luận", "II. Kết luận của chủ trì", "III. Phân công nhiệm vụ");
            assertThat(lines).contains("Thời gian: 09:00 14/10/2026", "Chủ trì: Nguyễn Văn A", "Thảo luận tiến độ dự án.");
            assertThat(text).contains("1. Ra mắt vào thứ Hai\n2. Tăng ngân sách");
            // Dòng chấm và gợi ý của mẫu đã được thay; mục AI trả rỗng ghi "Chưa có thông tin".
            assertThat(text).doesNotContain("……").doesNotContain("Ghi tóm tắt");
            assertThat(lines).contains("Nơi nhận: " + MinutesTemplateRenderer.NO_INFO);
            assertThat(lines.get(lines.size() - 1)).contains("chưa qua duyệt");
            // Thứ tự mục không đổi.
            assertThat(lines.indexOf("I. Nội dung thảo luận")).isLessThan(lines.indexOf("II. Kết luận của chủ trì"));
        }
    }

    @Test
    void pdfTemplateProducesNewDocxInTemplateOrder() throws Exception {
        Template t = MinutesTemplateReader.read("mau.pdf", pdf(List.of("BIEN BAN HOP", "Thoi gian: ......", "I. Noi dung thao luan", "II. Ket luan")));
        Map<String, String> content = Map.of("M1", "09:00", "M2", "Noi dung A", "M3", "Ket luan B");

        byte[] result = MinutesTemplateRenderer.buildNew(t, "BIÊN BẢN CUỘC HỌP", content, null);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(result))) {
            List<String> lines = doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();
            assertThat(lines).containsExactly("BIEN BAN HOP", "Thoi gian: 09:00", "I. Noi dung thao luan", "Noi dung A", "II. Ket luan", "Ket luan B");
        }
    }
}
