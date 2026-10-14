package vn.com.d2s.smr.service.mt.minutes;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Section;
import vn.com.d2s.smr.service.mt.minutes.MinutesTemplateReader.Template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dựng file DOCX biên bản theo mẫu người dùng tải lên (P13 bổ sung).
 * Mẫu .docx: điền vào chính file mẫu (giữ trình bày). Mẫu .pdf: dựng DOCX mới theo thứ tự và tên các mục.
 */
public final class MinutesTemplateRenderer {

    public static final String NO_INFO = "Chưa có thông tin";

    private MinutesTemplateRenderer() {
    }

    /** Điền nội dung từng mục vào chính file mẫu .docx. */
    public static byte[] fillDocx(byte[] templateBytes, Template template, Map<String, String> contentById, String footerNote) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<IBodyElement> body = new ArrayList<>(doc.getBodyElements());
            // Đi từ cuối lên để việc xoá / chèn đoạn không làm lệch các mục phía trên.
            for (int i = template.sections().size() - 1; i >= 0; i--) {
                Section s = template.sections().get(i);
                if (s.bodyIndex() < 0 || s.bodyIndex() >= body.size() || !(body.get(s.bodyIndex()) instanceof XWPFParagraph heading)) {
                    continue;
                }
                String content = valueOf(contentById, s);
                if (s.inline()) {
                    fillInline(heading, s.title(), content);
                    continue;
                }
                List<XWPFParagraph> old = new ArrayList<>();
                for (int j = s.bodyIndex() + 1; j < Math.min(s.bodyEnd(), body.size()); j++) {
                    if (body.get(j) instanceof XWPFParagraph p) old.add(p);
                }
                XWPFParagraph target;
                if (old.isEmpty()) {
                    target = insertAfter(doc, heading);
                } else {
                    target = old.get(0);
                    for (int j = old.size() - 1; j >= 1; j--) {
                        int pos = doc.getPosOfParagraph(old.get(j));
                        if (pos >= 0) doc.removeBodyElement(pos);
                    }
                }
                setText(target, content, false);
            }
            if (footerNote != null && !footerNote.isBlank()) addNote(doc, footerNote);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file DOCX từ mẫu.", e);
        }
    }

    /** Mẫu PDF (không sửa tại chỗ được): dựng DOCX mới theo bố cục mục của mẫu. */
    public static byte[] buildNew(Template template, String fallbackTitle, Map<String, String> contentById, String footerNote) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun t = title.createRun();
            t.setBold(true);
            t.setFontSize(16);
            t.setText(template.documentTitle() != null ? template.documentTitle() : fallbackTitle);
            for (Section s : template.sections()) {
                String content = valueOf(contentById, s);
                XWPFParagraph p = doc.createParagraph();
                XWPFRun label = p.createRun();
                label.setBold(true);
                if (s.inline()) {
                    label.setText(s.title() + ": ");
                    writeLines(p.createRun(), content);
                } else {
                    label.setText(s.title());
                    writeLines(doc.createParagraph().createRun(), content);
                }
            }
            if (footerNote != null && !footerNote.isBlank()) addNote(doc, footerNote);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file DOCX.", e);
        }
    }

    private static String valueOf(Map<String, String> contentById, Section s) {
        String v = contentById.get(s.id());
        return v == null || v.isBlank() ? NO_INFO : v.trim();
    }

    /** "Nhãn: ……" → giữ định dạng của nhãn, giá trị viết thường ngay sau. */
    private static void fillInline(XWPFParagraph p, String label, String value) {
        List<XWPFRun> runs = p.getRuns();
        String font = runs.isEmpty() ? null : runs.get(0).getFontFamily();
        Double size = runs.isEmpty() ? null : runs.get(0).getFontSizeAsDouble();
        for (int i = runs.size() - 1; i > 0; i--) p.removeRun(i);
        XWPFRun first = p.getRuns().isEmpty() ? p.createRun() : p.getRuns().get(0);
        first.setText(label + ": ", 0);
        XWPFRun v = p.createRun();
        if (font != null) v.setFontFamily(font);
        if (size != null) v.setFontSize(size);
        writeLines(v, value);
    }

    private static void setText(XWPFParagraph p, String text, boolean bold) {
        List<XWPFRun> runs = p.getRuns();
        String font = runs.isEmpty() ? null : runs.get(0).getFontFamily();
        Double size = runs.isEmpty() ? null : runs.get(0).getFontSizeAsDouble();
        for (int i = runs.size() - 1; i >= 0; i--) p.removeRun(i);
        XWPFRun run = p.createRun();
        if (font != null) run.setFontFamily(font);
        if (size != null) run.setFontSize(size);
        run.setBold(bold);
        writeLines(run, text);
    }

    private static void writeLines(XWPFRun run, String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }

    private static XWPFParagraph insertAfter(XWPFDocument doc, XWPFParagraph anchor) {
        try (XmlCursor cursor = anchor.getCTP().newCursor()) {
            if (cursor.toNextSibling()) {
                XWPFParagraph p = doc.insertNewParagraph(cursor);
                if (p != null) return p;
            }
        }
        return doc.createParagraph();
    }

    private static void addNote(XWPFDocument doc, String note) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(240);
        XWPFRun r = p.createRun();
        r.setItalic(true);
        r.setFontSize(9);
        r.setColor("808080");
        r.setText(note);
    }
}
