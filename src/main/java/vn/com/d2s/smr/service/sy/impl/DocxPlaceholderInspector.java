package vn.com.d2s.smr.service.sy.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Đọc nội dung chữ của file DOCX để kiểm tra placeholder {{...}}.
 * Word hay tách một placeholder thành nhiều thẻ &lt;w:t&gt; ⇒ bỏ hết thẻ XML rồi mới tìm.
 */
public final class DocxPlaceholderInspector {

    /** Placeholder bắt buộc của mẫu biên bản (SMR-Scope P7). */
    public static final List<String> REQUIRED_PLACEHOLDERS = List.of(
            "{{meeting_name}}", "{{time}}", "{{participants}}", "{{summary}}", "{{decisions}}", "{{action_items}}"
    );

    private static final long MAX_XML_BYTES = 20L * 1024 * 1024;

    private DocxPlaceholderInspector() {
    }

    /** Trả về danh sách placeholder bắt buộc còn thiếu. Ném IllegalArgumentException nếu không phải DOCX hợp lệ. */
    public static List<String> missingPlaceholders(InputStream docx) {
        String text = extractText(docx);
        return REQUIRED_PLACEHOLDERS.stream().filter(p -> !text.contains(p)).toList();
    }

    static String extractText(InputStream docx) {
        try (ZipInputStream zip = new ZipInputStream(docx, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = readLimited(zip);
                    return xml.replaceAll("<[^>]+>", "")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file DOCX.", e);
        }
        throw new IllegalArgumentException("File không phải DOCX hợp lệ (thiếu word/document.xml).");
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_XML_BYTES) {
                throw new IllegalArgumentException("Nội dung DOCX quá lớn.");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
