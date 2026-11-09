package vn.com.d2s.smr.service.mt.minutes;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Trích văn bản từ tài liệu đính kèm cuộc họp (PDF, DOCX, TXT/MD/CSV/JSON) để làm nguồn cho AI lập biên bản.
 * Định dạng khác (ảnh, video, XLSX...) trả null — bị bỏ qua, không làm hỏng việc sinh biên bản.
 */
public final class MeetingDocumentText {

    /** Đọc tối đa từng này byte của một tệp — chặn tệp quá lớn làm tràn bộ nhớ. */
    static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final Set<String> PLAIN = Set.of(".txt", ".md", ".csv", ".json");

    private MeetingDocumentText() {
    }

    public static boolean supports(String extension) {
        String ext = norm(extension);
        return ext.equals(".pdf") || ext.equals(".docx") || PLAIN.contains(ext);
    }

    /** @return văn bản đã gọn khoảng trắng, hoặc null nếu không đọc được / không hỗ trợ. Người gọi đóng stream. */
    public static String extract(InputStream in, String extension, long size) {
        String ext = norm(extension);
        if (!supports(ext) || size > MAX_BYTES) return null;
        try {
            byte[] bytes = in.readNBytes((int) MAX_BYTES);
            String text;
            if (ext.equals(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    text = new PDFTextStripper().getText(doc);
                }
            } else if (ext.equals(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes));
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    text = ex.getText();
                }
            } else {
                text = new String(bytes, StandardCharsets.UTF_8);
            }
            String clean = text == null ? "" : text.replace("\r", "").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
            return clean.isEmpty() ? null : clean;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String norm(String ext) {
        if (ext == null) return "";
        String e = ext.toLowerCase(Locale.ROOT).trim();
        return e.startsWith(".") || e.isEmpty() ? e : "." + e;
    }
}
