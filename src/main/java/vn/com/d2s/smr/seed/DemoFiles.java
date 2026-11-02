package vn.com.d2s.smr.seed;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import vn.com.d2s.smr.common.TextSearch;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Tạo nội dung file demo (DOCX / XLSX / PDF / PNG) theo tên file. Nội dung giả lập, chỉ để hệ thống có tài liệu thật để mở.
 * PDF dùng phông Arial của Windows nếu có (để hiện dấu tiếng Việt), không có thì bỏ dấu.
 */
public final class DemoFiles {

    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/Library/Fonts/Arial.ttf");

    private DemoFiles() {
    }

    public record Generated(byte[] bytes, String mime, String extension) {
    }

    /** title: tiêu đề hiển thị trong file (thường là tên cuộc họp / công việc). */
    public static Generated create(String fileName, String title, String company) {
        String lower = fileName.toLowerCase();
        String pretty = fileName.substring(0, fileName.lastIndexOf('.')).replace('_', ' ');
        List<String> body = List.of(
                company + " — tài liệu nội bộ.",
                "Nội dung: " + pretty + ".",
                "Liên quan: " + title + ".",
                "Tài liệu do bộ dữ liệu demo của hệ thống Smart Meeting tạo ra để minh họa.");
        try {
            if (lower.endsWith(".docx")) return new Generated(docx(pretty.toUpperCase(), body), DOCX, ".docx");
            if (lower.endsWith(".xlsx")) return new Generated(xlsx(pretty), XLSX, ".xlsx");
            if (lower.endsWith(".pdf")) return new Generated(pdf(pretty.toUpperCase(), body), "application/pdf", ".pdf");
            if (lower.endsWith(".png")) return new Generated(png(pretty), "image/png", ".png");
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file demo " + fileName, e);
        }
        throw new IllegalArgumentException("Không hỗ trợ tạo file demo: " + fileName);
    }

    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static byte[] docx(String heading, List<String> lines) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun t = title.createRun();
            t.setBold(true);
            t.setFontSize(15);
            t.setText(heading);
            for (String line : lines) doc.createParagraph().createRun().setText(line);
            doc.write(out);
            return out.toByteArray();
        }
    }

    static byte[] xlsx(String title) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Dữ liệu");
            String[] head = {"STT", "Hạng mục", "Phụ trách", "Giá trị (triệu đồng)", "Ghi chú"};
            var row = sheet.createRow(0);
            for (int i = 0; i < head.length; i++) row.createCell(i).setCellValue(head[i]);
            String[][] items = {
                    {"Chuẩn bị", "Phòng ban liên quan", "120"}, {"Triển khai", "Nhóm dự án", "340"},
                    {"Kiểm thử", "Phòng Đảm bảo chất lượng", "85"}, {"Vận hành", "Phòng Hạ tầng", "60"}, {"Dự phòng", "Ban Giám đốc", "45"}};
            for (int i = 0; i < items.length; i++) {
                var r = sheet.createRow(i + 1);
                r.createCell(0).setCellValue(i + 1);
                r.createCell(1).setCellValue(items[i][0]);
                r.createCell(2).setCellValue(items[i][1]);
                r.createCell(3).setCellValue(Double.parseDouble(items[i][2]));
                r.createCell(4).setCellValue(title);
            }
            for (int i = 0; i < head.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Mẫu biên bản dạng PDF (để thử chức năng tải mẫu PDF). */
    public static byte[] pdfTemplate(String company) throws IOException {
        return pdf("BIÊN BẢN HỌP", List.of(company.toUpperCase(), "Thời gian: ..........", "Thành phần tham dự: ..........",
                "I. NỘI DUNG CHÍNH", "(Tóm tắt nội dung thảo luận)", "II. KẾT LUẬN", "III. PHÂN CÔNG THỰC HIỆN", "Nơi nhận: .........."));
    }

    /** Mẫu biên bản hành chính dạng DOCX, không có placeholder (để thử chức năng tải mẫu DOCX). */
    public static byte[] docxTemplate(String company) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            bold(doc, "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", ParagraphAlignment.CENTER);
            bold(doc, "Độc lập – Tự do – Hạnh phúc", ParagraphAlignment.CENTER);
            bold(doc, "BIÊN BẢN CUỘC HỌP", ParagraphAlignment.CENTER);
            plain(doc, "Thời gian: ……………………");
            plain(doc, "Địa điểm: ……………………");
            plain(doc, "Chủ trì: ……………………");
            plain(doc, "Thư ký: ……………………");
            plain(doc, "Thành phần tham dự: ……………………");
            bold(doc, "I. Nội dung cuộc họp", null);
            plain(doc, "(Tóm tắt các ý kiến trao đổi chính)");
            bold(doc, "II. Kết luận của chủ trì", null);
            plain(doc, "…………………………………………");
            bold(doc, "III. Phân công thực hiện", null);
            plain(doc, "…………………………………………");
            bold(doc, "IV. Kiến nghị", null);
            plain(doc, "…………………………………………");
            plain(doc, "Nơi nhận: ……………………");
            plain(doc, company);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void bold(XWPFDocument doc, String text, ParagraphAlignment align) {
        var p = doc.createParagraph();
        if (align != null) p.setAlignment(align);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setText(text);
    }

    private static void plain(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    static byte[] pdf(String heading, List<String> lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDFont font = null;
            for (String path : FONT_CANDIDATES) {
                File f = new File(path);
                if (f.isFile()) {
                    font = PDType0Font.load(doc, f);
                    break;
                }
            }
            boolean unicode = font != null;
            if (font == null) font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 740;
                cs.beginText();
                cs.setFont(font, 15);
                cs.newLineAtOffset(60, y);
                cs.showText(unicode ? heading : ascii(heading));
                cs.endText();
                for (String line : lines) {
                    y -= 26;
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(60, y);
                    cs.showText(unicode ? line : ascii(line));
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String ascii(String s) {
        // Bỏ dấu nhưng giữ chữ hoa / thường (TextSearch.fold đã chuyển về chữ thường).
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            String f = TextSearch.fold(String.valueOf(c));
            char o = f.isEmpty() ? '?' : f.charAt(0);
            sb.append(Character.isUpperCase(c) ? Character.toUpperCase(o) : o);
        }
        return sb.toString().replace('…', '.').replace('–', '-');
    }

    static byte[] png(String title) throws IOException {
        BufferedImage img = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x0B5CAB), 960, 540, new Color(0x36A3F7)));
        g.fillRect(0, 0, 960, 540);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 54));
        g.drawString("Smart Meeting v2", 80, 250);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
        g.drawString("Họp xong, biên bản xong", 80, 310);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        g.drawString(title, 80, 480);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
