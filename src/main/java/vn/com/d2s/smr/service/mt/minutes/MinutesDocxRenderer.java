package vn.com.d2s.smr.service.mt.minutes;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Xuất biên bản ra DOCX (P13) theo mẫu đã tải lên ở P7 (placeholder {{meeting_name}}, {{time}}, {{participants}},
 * {{summary}}, {{decisions}}, {{action_items}}). Word hay tách placeholder thành nhiều run → ghép cả đoạn rồi thay.
 * Chưa có mẫu thì dựng file mặc định.
 */
public final class MinutesDocxRenderer {

    private MinutesDocxRenderer() {
    }

    public static byte[] render(InputStream template, Map<String, String> values) {
        try (XWPFDocument doc = template != null ? new XWPFDocument(template) : defaultDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            replaceIn(doc.getParagraphs(), values);
            for (XWPFTable t : doc.getTables()) replaceInTable(t, values);
            for (XWPFHeaderFooter h : doc.getHeaderList()) replaceIn(h.getParagraphs(), values);
            for (XWPFHeaderFooter f : doc.getFooterList()) replaceIn(f.getParagraphs(), values);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file DOCX.", e);
        }
    }

    private static void replaceInTable(XWPFTable table, Map<String, String> values) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                replaceIn(cell.getParagraphs(), values);
                for (XWPFTable nested : cell.getTables()) replaceInTable(nested, values);
            }
        }
    }

    private static void replaceIn(List<XWPFParagraph> paragraphs, Map<String, String> values) {
        for (XWPFParagraph p : new ArrayList<>(paragraphs)) {
            String text = p.getText();
            if (text == null || !text.contains("{{")) continue;
            String replaced = text;
            for (Map.Entry<String, String> e : values.entrySet()) {
                replaced = replaced.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
            if (replaced.equals(text)) continue;
            List<XWPFRun> runs = p.getRuns();
            if (runs.isEmpty()) continue;
            // Giữ định dạng của run đầu tiên, xoá các run còn lại.
            for (int i = runs.size() - 1; i > 0; i--) p.removeRun(i);
            XWPFRun run = runs.get(0);
            run.setText("", 0);
            String[] lines = replaced.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) run.setText(lines[0], 0);
                else {
                    run.addBreak();
                    run.setText(lines[i]);
                }
            }
        }
    }

    private static XWPFDocument defaultDocument() {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun t = title.createRun();
        t.setBold(true);
        t.setFontSize(16);
        t.setText("BIÊN BẢN CUỘC HỌP");
        add(doc, "Cuộc họp: ", "{{meeting_name}}");
        add(doc, "Thời gian: ", "{{time}}");
        add(doc, "Thành phần tham dự:", null);
        doc.createParagraph().createRun().setText("{{participants}}");
        add(doc, "I. Tóm tắt nội dung", null);
        doc.createParagraph().createRun().setText("{{summary}}");
        add(doc, "II. Các quyết định", null);
        doc.createParagraph().createRun().setText("{{decisions}}");
        add(doc, "III. Việc cần làm", null);
        doc.createParagraph().createRun().setText("{{action_items}}");
        return doc;
    }

    private static void add(XWPFDocument doc, String label, String placeholder) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setText(label);
        if (placeholder != null) {
            XWPFRun v = p.createRun();
            v.setText(placeholder);
        }
    }
}
