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
        for (XWPFHeaderFooter f : doc.getFooterList()) for (XWPFParagraph p : f.getParagraphs()) fontParagraph(p);
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
            if (r.getFontSize() < 0) r.setFontSize(FONT_SIZE);
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

    // ===================== Bố cục mặc định chuyên nghiệp =====================

    private static final int TW_CM = 567; // 1 cm = 567 twips

    /** Dựng biên bản theo bố cục hành chính: đầu trang công ty, thông tin, thành phần, nội dung, quyết định, việc cần làm, chữ ký. */
    public static byte[] renderDefault(MinutesDocModel m) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            margins(doc);
            letterhead(doc, m);
            para(doc, "BIÊN BẢN CUỘC HỌP", true, false, ParagraphAlignment.CENTER, 14, 120);
            if (notBlank(m.meetingName())) para(doc, m.meetingName(), false, true, ParagraphAlignment.CENTER, FONT_SIZE, 240);

            infoTable(doc, m);

            heading(doc, "I. THÀNH PHẦN THAM DỰ");
            if (m.participants().isEmpty()) {
                para(doc, "Không có thông tin.", false, true, ParagraphAlignment.LEFT, FONT_SIZE, 120);
            } else {
                List<String[]> rows = new ArrayList<>();
                for (int i = 0; i < m.participants().size(); i++) {
                    MinutesDocModel.Person p = m.participants().get(i);
                    rows.add(new String[]{String.valueOf(i + 1), p.name(), p.role()});
                }
                gridTable(doc, new String[]{"STT", "Họ và tên", "Vai trò"}, new int[]{900, 5600, 2500}, new boolean[]{true, false, true}, rows);
            }

            heading(doc, "II. NỘI DUNG CUỘC HỌP");
            if (m.summaryParagraphs().isEmpty()) {
                para(doc, "Không có thông tin.", false, true, ParagraphAlignment.LEFT, FONT_SIZE, 120);
            }
            for (String s : m.summaryParagraphs()) body(doc, s);
            for (int i = 0; i < m.discussion().size(); i++) {
                MinutesDocModel.Discussion d = m.discussion().get(i);
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.BOTH);
                p.setSpacingBetween(1.3);
                p.setSpacingAfter(100);
                XWPFRun head = p.createRun();
                head.setBold(true);
                head.setText((i + 1) + ". " + (d.topic().isBlank() ? "Nội dung thảo luận" : d.topic()) + ": ");
                p.createRun().setText(d.text());
            }

            heading(doc, "III. KẾT LUẬN VÀ QUYẾT ĐỊNH");
            if (m.decisions().isEmpty()) {
                para(doc, "Không có quyết định nào được ghi nhận.", false, true, ParagraphAlignment.LEFT, FONT_SIZE, 120);
            }
            for (int i = 0; i < m.decisions().size(); i++) body(doc, (i + 1) + ". " + m.decisions().get(i));

            heading(doc, "IV. VIỆC CẦN LÀM");
            if (m.actions().isEmpty()) {
                para(doc, "Không có việc cần làm.", false, true, ParagraphAlignment.LEFT, FONT_SIZE, 120);
            } else {
                List<String[]> rows = new ArrayList<>();
                for (int i = 0; i < m.actions().size(); i++) {
                    MinutesDocModel.Action a = m.actions().get(i);
                    rows.add(new String[]{String.valueOf(i + 1), a.text(), a.assignee(), a.deadline()});
                }
                gridTable(doc, ACTION_HEADERS, ACTION_WIDTHS, new boolean[]{true, false, false, true}, rows);
            }

            if (notBlank(m.footerNote())) para(doc, m.footerNote(), false, true, ParagraphAlignment.LEFT, 11, 120);
            para(doc, "", false, false, ParagraphAlignment.LEFT, FONT_SIZE, 0);
            signatures(doc, m);
            pageFooter(doc, m.company());
            applyFont(doc);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file DOCX.", e);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Khổ A4, lề trái 3 cm, các lề còn lại 2 cm. */
    private static void margins(XWPFDocument doc) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr sect =
                doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        size.setW(java.math.BigInteger.valueOf(11906));
        size.setH(java.math.BigInteger.valueOf(16838));
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar mar = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        mar.setTop(java.math.BigInteger.valueOf(2L * TW_CM));
        mar.setBottom(java.math.BigInteger.valueOf(2L * TW_CM));
        mar.setLeft(java.math.BigInteger.valueOf(3L * TW_CM));
        mar.setRight(java.math.BigInteger.valueOf(2L * TW_CM));
    }

    private static XWPFParagraph para(XWPFDocument doc, String text, boolean bold, boolean italic, ParagraphAlignment align, int size, int after) {
        XWPFParagraph p = doc.createParagraph();
        fillPara(p, text, bold, italic, align, size, after);
        return p;
    }

    private static void fillPara(XWPFParagraph p, String text, boolean bold, boolean italic, ParagraphAlignment align, int size, int after) {
        p.setAlignment(align);
        p.setSpacingAfter(after);
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setItalic(italic);
        r.setFontSize(size);
        r.setText(text);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = para(doc, text, true, false, ParagraphAlignment.LEFT, FONT_SIZE, 100);
        p.setSpacingBefore(240);
        p.setKeepNext(true);
    }

    /** Đoạn nội dung: căn đều, thụt dòng đầu 1 cm, giãn dòng 1,3. */
    private static void body(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setFirstLineIndent(TW_CM);
        p.setSpacingBetween(1.3);
        p.setSpacingAfter(100);
        p.createRun().setText(text);
    }

    private static XWPFTable noBorderTable(XWPFDocument doc, int rows, int[] widths) {
        XWPFTable t = doc.createTable(rows, widths.length);
        t.setWidth("100%");
        var none = XWPFTable.XWPFBorderType.NONE;
        t.setTopBorder(none, 0, 0, "FFFFFF");
        t.setBottomBorder(none, 0, 0, "FFFFFF");
        t.setLeftBorder(none, 0, 0, "FFFFFF");
        t.setRightBorder(none, 0, 0, "FFFFFF");
        t.setInsideHBorder(none, 0, 0, "FFFFFF");
        t.setInsideVBorder(none, 0, 0, "FFFFFF");
        for (XWPFTableRow row : t.getRows()) {
            for (int c = 0; c < widths.length; c++) row.getCell(c).setWidth(String.valueOf(widths[c]));
        }
        return t;
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold, boolean italic, ParagraphAlignment align) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        for (int i = p.getRuns().size() - 1; i >= 0; i--) p.removeRun(i);
        p.setAlignment(align);
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setItalic(italic);
        String[] lines = (text == null ? "" : text).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) r.addBreak();
            r.setText(lines[i]);
        }
    }

    /** Đầu văn bản: tên công ty bên trái, quốc hiệu bên phải (nếu bật). */
    private static void letterhead(XWPFDocument doc, MinutesDocModel m) {
        XWPFTable t = noBorderTable(doc, 1, new int[]{4300, 4700});
        XWPFTableRow row = t.getRow(0);
        setCell(row.getCell(0), (m.company() == null ? "" : m.company()).toUpperCase(java.util.Locale.ROOT), true, false, ParagraphAlignment.CENTER);
        if (m.nationalHeader()) {
            setCell(row.getCell(1), "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM\nĐộc lập – Tự do – Hạnh phúc", true, false, ParagraphAlignment.CENTER);
        } else {
            setCell(row.getCell(1), "", false, false, ParagraphAlignment.CENTER);
        }
        // Đường kẻ ngắn dưới tên công ty.
        row.getCell(0).getCTTc().addNewTcPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders b = row.getCell(0).getCTTc().getTcPr().addNewTcBorders();
        b.addNewBottom().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.SINGLE);
        para(doc, "", false, false, ParagraphAlignment.LEFT, 8, 0);
    }

    /** Thời gian, hình thức, chủ trì, thư ký: nhãn đậm, giá trị thường. */
    private static void infoTable(XWPFDocument doc, MinutesDocModel m) {
        List<String[]> info = new ArrayList<>();
        info.add(new String[]{"Thời gian:", m.time()});
        info.add(new String[]{"Hình thức:", "Họp trực tuyến"});
        if (notBlank(m.chair())) info.add(new String[]{"Chủ trì:", m.chair()});
        if (notBlank(m.secretary())) info.add(new String[]{"Thư ký:", m.secretary()});
        XWPFTable t = noBorderTable(doc, info.size(), new int[]{1900, 7100});
        for (int i = 0; i < info.size(); i++) {
            setCell(t.getRow(i).getCell(0), info.get(i)[0], true, false, ParagraphAlignment.LEFT);
            setCell(t.getRow(i).getCell(1), info.get(i)[1], false, false, ParagraphAlignment.LEFT);
        }
    }

    /** Bảng có viền, hàng tiêu đề tô xám nhạt, cột đánh dấu {@code center} căn giữa. */
    private static void gridTable(XWPFDocument doc, String[] headers, int[] widths, boolean[] center, List<String[]> rows) {
        XWPFTable t = doc.createTable(rows.size() + 1, headers.length);
        t.setWidth("100%");
        var single = XWPFTable.XWPFBorderType.SINGLE;
        t.setTopBorder(single, 4, 0, "000000");
        t.setBottomBorder(single, 4, 0, "000000");
        t.setLeftBorder(single, 4, 0, "000000");
        t.setRightBorder(single, 4, 0, "000000");
        t.setInsideHBorder(single, 4, 0, "000000");
        t.setInsideVBorder(single, 4, 0, "000000");
        XWPFTableRow head = t.getRow(0);
        head.setRepeatHeader(true);
        for (int c = 0; c < headers.length; c++) {
            XWPFTableCell cell = head.getCell(c);
            cell.setWidth(String.valueOf(widths[c]));
            cell.setColor("D9D9D9");
            setCell(cell, headers[c], true, false, ParagraphAlignment.CENTER);
        }
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = t.getRow(r + 1);
            for (int c = 0; c < headers.length; c++) {
                XWPFTableCell cell = row.getCell(c);
                cell.setWidth(String.valueOf(widths[c]));
                String value = c < rows.get(r).length && rows.get(r)[c] != null ? rows.get(r)[c] : "";
                boolean pending = value.equals("Chưa xác định");
                setCell(cell, value, false, pending, center[c] ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
            }
        }
    }

    /** Hai khối chữ ký: Thư ký (trái), Chủ trì (phải). */
    private static void signatures(XWPFDocument doc, MinutesDocModel m) {
        XWPFTable t = noBorderTable(doc, 1, new int[]{4500, 4500});
        XWPFTableRow row = t.getRow(0);
        signBlock(row.getCell(0), "THƯ KÝ", m.secretary());
        signBlock(row.getCell(1), "CHỦ TRÌ", m.chair());
        row.getCtRow().addNewTrPr().addNewCantSplit();
    }

    private static void signBlock(XWPFTableCell cell, String title, String name) {
        setCell(cell, title, true, false, ParagraphAlignment.CENTER);
        XWPFParagraph hint = cell.addParagraph();
        fillPara(hint, "(Ký, ghi rõ họ tên)", false, true, ParagraphAlignment.CENTER, 11, 0);
        for (int i = 0; i < 3; i++) cell.addParagraph();
        XWPFParagraph n = cell.addParagraph();
        fillPara(n, name == null ? "" : name, true, false, ParagraphAlignment.CENTER, FONT_SIZE, 0);
    }

    /** Chân trang: tên công ty và "Trang X / Y". */
    private static void pageFooter(XWPFDocument doc, String company) {
        org.apache.poi.xwpf.usermodel.XWPFFooter footer = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun a = p.createRun();
        a.setFontSize(10);
        a.setItalic(true);
        a.setText((company == null ? "" : company) + "  |  Trang ");
        field(p, "PAGE");
        XWPFRun sep = p.createRun();
        sep.setFontSize(10);
        sep.setItalic(true);
        sep.setText(" / ");
        field(p, "NUMPAGES");
    }

    private static void field(XWPFParagraph p, String instr) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField f = p.getCTP().addNewFldSimple();
        f.setInstr(" " + instr + " ");
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR r = f.addNewR();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr pr = r.addNewRPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts fonts = pr.addNewRFonts();
        fonts.setAscii(FONT);
        fonts.setHAnsi(FONT);
        fonts.setCs(FONT);
        pr.addNewI();
        pr.addNewSz().setVal(java.math.BigInteger.valueOf(20));
        r.addNewT().setStringValue("1");
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
