package vn.com.d2s.smr.service.mt.minutes;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import vn.com.d2s.smr.common.TextSearch;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc bố cục mẫu biên bản do người dùng tải lên (P13 bổ sung): .docx (POI) hoặc .pdf (PDFBox) → danh sách mục.
 * <ul>
 *   <li><b>Mục khối</b>: một dòng tiêu đề (heading / in đậm / in hoa / đánh số "I.", "1.", "Điều 1") + phần chữ bên dưới.</li>
 *   <li><b>Mục dòng</b>: "Nhãn: ……" trên cùng một dòng (Thời gian, Địa điểm, Chủ trì...).</li>
 * </ul>
 * Mẫu có placeholder {{...}} kiểu P7 thì không tách mục — điền placeholder như cũ.
 */
public final class MinutesTemplateReader {

    public static final int MAX_SECTIONS = 40;
    public static final int MAX_CHARS = 12_000;

    private static final Pattern NUMBERED = Pattern.compile(
            "^\\s*((?:[IVXLC]{1,5}|\\d{1,2}(?:\\.\\d{1,2})*)[.)]|(?:Điều|Phần|Mục|Chương)\\s+[\\dIVXLC]+[.:]?)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE = Pattern.compile("^\\s*([^:：]{2,60})[:：]\\s*(.*)$");
    private static final Pattern FILLER = Pattern.compile("^[\\s._…‥\\-–—*/]*$");

    private MinutesTemplateReader() {
    }

    public enum Format { DOCX, PDF }

    /** Loại nội dung của mục: dữ liệu có sẵn (hệ thống điền) hay AI viết. */
    public enum Kind { MEETING_NAME, TIME, PLACE, HOST, SECRETARY, PARTICIPANTS, DECISIONS, ACTIONS, AI }

    /**
     * @param inline     true = "Nhãn: giá trị" trên cùng dòng
     * @param bodyIndex  vị trí phần tử tiêu đề trong thân DOCX (-1 với PDF)
     * @param bodyEnd    vị trí phần tử đầu tiên KHÔNG thuộc mục (DOCX)
     */
    public record Section(String id, String title, String hint, Kind kind, boolean inline, int bodyIndex, int bodyEnd) {
    }

    public record Template(Format format, String documentTitle, List<Section> sections, boolean placeholderBased) {
        public List<String> sectionTitles() {
            return sections.stream().map(Section::title).toList();
        }
    }

    public static Format formatOf(String fileName, byte[] bytes) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        boolean zip = bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K';
        boolean pdf = bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (lower.endsWith(".docx") && zip) return Format.DOCX;
        if (lower.endsWith(".pdf") && pdf) return Format.PDF;
        if (lower.endsWith(".doc")) {
            throw new IllegalArgumentException("Chưa hỗ trợ file .doc (Word cũ). Hãy mở bằng Word và lưu lại thành .docx.");
        }
        throw new IllegalArgumentException("Mẫu biên bản phải là file .docx hoặc .pdf hợp lệ.");
    }

    public static Template read(String fileName, byte[] bytes) {
        return formatOf(fileName, bytes) == Format.DOCX ? readDocx(bytes) : readPdf(bytes);
    }

    // ===================== DOCX =====================

    static Template readDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<IBodyElement> body = doc.getBodyElements();
            StringBuilder all = new StringBuilder();
            record Line(int index, String text, boolean heading) {
            }
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < body.size(); i++) {
                if (body.get(i) instanceof XWPFParagraph p) {
                    String text = p.getText() == null ? "" : p.getText().trim();
                    all.append(text).append('\n');
                    if (!text.isEmpty()) lines.add(new Line(i, text, isDocxHeading(p, text)));
                }
            }
            String whole = all.toString();
            if (whole.contains("{{") && whole.contains("}}")) {
                return new Template(Format.DOCX, null, List.of(), true);
            }
            if (whole.length() > MAX_CHARS) {
                throw new IllegalArgumentException("Mẫu quá dài (" + whole.length() + " ký tự). Hãy dùng mẫu dưới " + MAX_CHARS + " ký tự.");
            }

            List<Section> sections = new ArrayList<>();
            String title = null;
            for (int li = 0; li < lines.size(); li++) {
                Line line = lines.get(li);
                Matcher inline = INLINE.matcher(line.text());
                boolean isInline = inline.matches() && FILLER.matcher(inline.group(2)).matches() && !NUMBERED.matcher(line.text()).matches();
                if (isInline) {
                    String label = inline.group(1).trim();
                    sections.add(new Section(id(sections), label, "", classify(label), true, line.index(), line.index() + 1));
                } else if (line.heading()) {
                    // Quốc hiệu, tiêu ngữ, tên cơ quan và tiêu đề văn bản ("BIÊN BẢN ...") ở đầu mẫu không phải mục cần điền.
                    String folded = TextSearch.fold(line.text());
                    if (sections.isEmpty() && (folded.contains("cong hoa xa hoi") || folded.contains("doc lap"))) continue;
                    if (title == null && sections.isEmpty() && folded.contains("bien ban")) {
                        title = line.text();
                        continue;
                    }
                    int end = body.size();
                    StringBuilder hint = new StringBuilder();
                    for (int lj = li + 1; lj < lines.size(); lj++) {
                        Line next = lines.get(lj);
                        Matcher m = INLINE.matcher(next.text());
                        boolean nextInline = m.matches() && FILLER.matcher(m.group(2)).matches();
                        if (next.heading() || nextInline) {
                            end = next.index();
                            break;
                        }
                        if (!FILLER.matcher(next.text()).matches() && hint.length() < 300) hint.append(next.text()).append(' ');
                    }
                    String clean = line.text().replaceAll("[:：]\\s*$", "").trim();
                    sections.add(new Section(id(sections), clean, hint.toString().trim(), classify(clean), false, line.index(), end));
                }
                if (sections.size() > MAX_SECTIONS) {
                    throw new IllegalArgumentException("Mẫu có quá nhiều mục (tối đa " + MAX_SECTIONS + ").");
                }
            }
            if (sections.isEmpty()) {
                throw new IllegalArgumentException("Không nhận ra mục nào trong mẫu. Mẫu cần có các tiêu đề mục (in đậm, đánh số) hoặc dòng dạng \"Nhãn: ……\".");
            }
            return new Template(Format.DOCX, title, sections, false);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file .docx (file hỏng hoặc có mật khẩu).");
        }
    }

    private static boolean isDocxHeading(XWPFParagraph p, String text) {
        if (text.length() > 160) return false;
        String style = p.getStyle() == null ? "" : p.getStyle().toLowerCase();
        if (style.startsWith("heading") || style.startsWith("tiu") || style.equals("title")) return true;
        if (NUMBERED.matcher(text).matches() && text.length() <= 120) return true;
        List<XWPFRun> runs = p.getRuns().stream().filter(r -> r.text() != null && !r.text().isBlank()).toList();
        boolean allBold = !runs.isEmpty() && runs.stream().allMatch(XWPFRun::isBold);
        return (allBold && text.length() <= 120) || isUpperLine(text);
    }

    // ===================== PDF =====================

    static Template readPdf(byte[] bytes) {
        String text;
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted()) throw new IllegalArgumentException("Mẫu PDF có mật khẩu nên không đọc được.");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(pdf);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file .pdf (file hỏng hoặc có mật khẩu).");
        }
        if (text == null || text.replaceAll("\\s", "").length() < 20) {
            throw new IllegalArgumentException("Mẫu PDF là ảnh chụp (scan) nên không đọc được chữ. Hãy dùng PDF có chữ hoặc file .docx.");
        }
        if (text.length() > MAX_CHARS) {
            throw new IllegalArgumentException("Mẫu quá dài (" + text.length() + " ký tự). Hãy dùng mẫu dưới " + MAX_CHARS + " ký tự.");
        }
        List<String> lines = text.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        List<Section> sections = new ArrayList<>();
        String title = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher inline = INLINE.matcher(line);
            boolean numbered = NUMBERED.matcher(line).matches() && line.length() <= 120;
            if (inline.matches() && FILLER.matcher(inline.group(2)).matches() && !numbered) {
                String label = inline.group(1).trim();
                sections.add(new Section(id(sections), label, "", classify(label), true, -1, -1));
            } else if (numbered || isUpperLine(line)) {
                if (title == null && sections.isEmpty() && TextSearch.fold(line).contains("bien ban")) {
                    title = line;
                    continue;
                }
                // Quốc hiệu, tên cơ quan ở đầu trang không phải mục.
                String folded = TextSearch.fold(line);
                if (!numbered && (folded.contains("cong hoa xa hoi") || folded.contains("doc lap") || title == null)) continue;
                StringBuilder hint = new StringBuilder();
                for (int j = i + 1; j < lines.size() && hint.length() < 300; j++) {
                    String next = lines.get(j);
                    if (NUMBERED.matcher(next).matches() || isUpperLine(next)) break;
                    if (!FILLER.matcher(next).matches()) hint.append(next).append(' ');
                }
                String clean = line.replaceAll("[:：]\\s*$", "").trim();
                sections.add(new Section(id(sections), clean, hint.toString().trim(), classify(clean), false, -1, -1));
            }
            if (sections.size() > MAX_SECTIONS) {
                throw new IllegalArgumentException("Mẫu có quá nhiều mục (tối đa " + MAX_SECTIONS + ").");
            }
        }
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Không nhận ra mục nào trong mẫu PDF. Mẫu cần có các tiêu đề mục đánh số (I., 1., Điều 1) hoặc dòng dạng \"Nhãn: ……\".");
        }
        return new Template(Format.PDF, title, sections, false);
    }

    // ===================== Chung =====================

    private static String id(List<Section> sections) {
        return "M" + (sections.size() + 1);
    }

    private static boolean isUpperLine(String text) {
        String letters = text.replaceAll("[^\\p{L}]", "");
        return letters.length() >= 4 && text.length() <= 120 && letters.equals(letters.toUpperCase()) && !letters.equals(letters.toLowerCase());
    }

    /** Đoán loại mục theo tên (đã bỏ dấu). Không khớp → AI viết. */
    public static Kind classify(String title) {
        String t = TextSearch.fold(title).replaceAll("^[\\s\\divxlc.)]+\\s+", "");
        if (has(t, "quyet dinh", "ket luan", "nghi quyet", "thong nhat")) return Kind.DECISIONS;
        if (has(t, "viec can lam", "phan cong", "nhiem vu", "giao viec", "cong viec can", "ke hoach thuc hien", "hanh dong tiep theo")) return Kind.ACTIONS;
        if (has(t, "chu tri", "chu toa")) return Kind.HOST;
        if (has(t, "thu ky")) return Kind.SECRETARY;
        if (has(t, "thanh phan", "tham du", "nguoi tham gia", "co mat")) return Kind.PARTICIPANTS;
        if (has(t, "thoi gian", "ngay hop", "vao luc", "bat dau luc")) return Kind.TIME;
        if (has(t, "dia diem", "noi hop") || t.equals("tai")) return Kind.PLACE;
        if (has(t, "ten cuoc hop", "ve viec", "chu de", "tieu de") || t.equals("cuoc hop")) return Kind.MEETING_NAME;
        return Kind.AI;
    }

    private static boolean has(String text, String... keys) {
        for (String k : keys) if (text.contains(k)) return true;
        return false;
    }
}
