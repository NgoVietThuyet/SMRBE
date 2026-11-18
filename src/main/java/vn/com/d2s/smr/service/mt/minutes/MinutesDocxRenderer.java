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
        return render(template, values, null);
    }

    /**
     * @param actionRows các dòng bảng việc cần làm (STT, tên công việc, người phụ trách, deadline); khác rỗng thì đoạn chỉ chứa
     *                   {{action_items}} được thay bằng bảng 4 cột. Mẫu mặc định luôn dùng Times New Roman cỡ 13.
     */
    public static byte[] render(InputStream template, Map<String, String> values, List<String[]> actionRows) {
        try (XWPFDocument doc = template != null ? new XWPFDocument(template) : defaultDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (actionRows != null && !actionRows.isEmpty()) replaceActionsWithTable(doc, actionRows, template == null);
            replaceIn(doc.getParagraphs(), values);
            for (XWPFTable t : doc.getTables()) replaceInTable(t, values);
            for (XWPFHeaderFooter h : doc.getHeaderList()) replaceIn(h.getParagraphs(), values);
            for (XWPFHeaderFooter f : doc.getFooterList()) replaceIn(f.getParagraphs(), values);
            if (template == null) applyFont(doc);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file DOCX.", e);
        }
    }

    static final String FONT = "Times New Roman";
    static final int FONT_SIZE = 13;
    private static final String[] ACTION_HEADERS = {"STT", "Tên công việc", "Người phụ trách", "Deadline"};
    private static final int[] ACTION_WIDTHS = {700, 4700, 2000, 1600};

    /** Thay đoạn chỉ gồm {{action_items}} bằng bảng có viền. */
    private static void replaceActionsWithTable(XWPFDocument doc, List<String[]> rows, boolean styled) {
        for (XWPFParagraph p : new ArrayList<>(doc.getParagraphs())) {
            String text = p.getText();
            if (text == null || !text.trim().equals("{{action_items}}")) continue;
            org.apache.xmlbeans.XmlCursor cursor = p.getCTP().newCursor();
            XWPFTable table = doc.insertNewTbl(cursor);
            cursor.dispose();
            table.setWidth("100%");
            table.setTableAlignment(org.apache.poi.xwpf.usermodel.TableRowAlign.CENTER);
            table.setInsideHBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
            table.setInsideVBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
            table.setTopBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
            table.setBottomBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
            table.setLeftBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
            table.setRightBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
            XWPFTableRow header = table.getRow(0);
            while (header.getTableCells().size() < 4) header.addNewTableCell();
            for (int c = 0; c < 4; c++) fillCell(header.getCell(c), ACTION_HEADERS[c], true, c != 1, ACTION_WIDTHS[c]);
            for (String[] r : rows) {
                XWPFTableRow row = table.createRow();
                while (row.getTableCells().size() < 4) row.addNewTableCell();
                for (int c = 0; c < 4; c++) fillCell(row.getCell(c), c < r.length && r[c] != null ? r[c] : "", false, c == 0, ACTION_WIDTHS[c]);
            }
            int pos = doc.getPosOfParagraph(p);
            doc.removeBodyElement(pos);
            return;
        }
    }

    private static void fillCell(XWPFTableCell cell, String text, boolean bold, boolean center, int width) {
        cell.setWidth(String.valueOf(width));
        XWPFParagraph para = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        para.setAlignment(center ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
        for (int i = para.getRuns().size() - 1; i >= 0; i--) para.removeRun(i);
        XWPFRun run = para.createRun();
        run.setBold(bold);
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }

    /** Ép Times New Roman cỡ 13 cho mọi run (cả ascii, hAnsi, cs, eastAsia để tiếng Việt không đổi font). */
    private static void applyFont(XWPFDocument doc) {
        for (XWPFParagraph p : doc.getParagraphs()) fontParagraph(p);
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow row : t.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) fontParagraph(p);
                }
            }
        }
    }

    private static void fontParagraph(XWPFParagraph p) {
        for (XWPFRun r : p.getRuns()) {
            r.setFontFamily(FONT);
            r.setFontSize(FONT_SIZE);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr rPr = r.getCTR().isSetRPr() ? r.getCTR().getRPr() : r.getCTR().addNewRPr();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
            fonts.setAscii(FONT);
            fonts.setHAnsi(FONT);
            fonts.setCs(FONT);
            fonts.setEastAsia(FONT);
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
        t.setFontSize(FONT_SIZE);
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
