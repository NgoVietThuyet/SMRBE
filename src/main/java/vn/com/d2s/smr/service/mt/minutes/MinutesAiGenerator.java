package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.com.d2s.smr.config.BusinessTime;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dựng prompt và đọc kết quả LLM cho biên bản (P13).
 * Transcript được đánh số [S1], [S2]... → LLM chỉ được trích các mã này làm nguồn → đổi lại thành Id đoạn.
 * Mục nào không có nguồn hợp lệ vẫn giữ (để người soạn thấy) nhưng có cảnh báo và không cho phát hành.
 */
public final class MinutesAiGenerator {

    public static final String PROMPT_VERSION = "minutes-v2";
    /** Giới hạn độ dài transcript gửi LLM (ký tự) — dài hơn thì cắt bớt phần cuối, có ghi chú trong prompt. */
    static final int MAX_TRANSCRIPT_CHARS = 24_000;
    /** Giới hạn tổng độ dài tài liệu đính kèm gửi LLM, và tối đa cho từng tài liệu (ký tự). */
    static final int MAX_DOCS_CHARS = 12_000;
    static final int MAX_DOC_CHARS = 5_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private MinutesAiGenerator() {
    }

    /** role = MeetingPersonal.type (1 chủ trì, 2 đồng chủ trì, 3 thư ký, 4+ thành viên); 0 = không rõ. */
    public record Participant(String userName, String fullName, int role) {
        public Participant(String userName, String fullName) {
            this(userName, fullName, 0);
        }
    }

    /** Quy tắc trọng số vai trò, dùng chung cho mọi prompt lập biên bản. */
    public static final String ROLE_RULES = """
            TRỌNG SỐ VAI TRÒ: mỗi người nói được gắn nhãn vai trò ([CHỦ TRÌ], [ĐỒNG CHỦ TRÌ], [THƯ KÝ], [THÀNH VIÊN]).
              - Lời kết luận, chốt, giao việc, đồng ý hoặc bác bỏ của [CHỦ TRÌ] / [ĐỒNG CHỦ TRÌ] là căn cứ chính cho quyết định cuối cùng và việc cần làm.
                Nếu [THÀNH VIÊN] nói khác chủ trì ở cùng chủ đề thì theo chủ trì; ý kiến thành viên vẫn ghi trong diễn biến thảo luận.
              - Thành viên đề xuất mà chủ trì chưa đồng ý hoặc chưa phản hồi chỉ là đề xuất (ghi trong diễn biến), chưa đưa vào quyết định
                cuối cùng trừ khi có lời chốt rõ ràng. Chủ trì đã chốt thì đưa vào quyết định dù thành viên không nhắc lại.
              - Việc chủ trì giao ghi đúng người được giao. Việc thành viên tự nhận vẫn ghi nếu chủ trì không bác bỏ.
              - [THƯ KÝ] chỉ ghi nhận, không tăng trọng số. Không bỏ hẳn ý kiến của thành viên khỏi diễn biến.
              - Khi kết luận do chủ trì chốt, ghi "(Chủ trì chốt)" ở cuối nội dung quyết định hoặc việc cần làm.
            """;

    /** Nhãn vai trò của người nói: khớp userName rồi họ tên (không phân biệt hoa thường). Không khớp (khách) → chuỗi rỗng. */
    public static String speakerTag(List<Participant> participants, String userName, String speakerName) {
        Participant found = null;
        for (Participant p : participants) {
            if (p.role() <= 0) continue;
            if (userName != null && !userName.isBlank() && p.userName() != null && p.userName().equalsIgnoreCase(userName.trim())) {
                found = p;
                break;
            }
            if (found == null && speakerName != null && p.fullName() != null
                    && normalize(p.fullName()).equals(normalize(speakerName))) {
                found = p;
            }
        }
        return found == null ? "" : " [" + roleLabel(found.role()).toUpperCase(java.util.Locale.ROOT) + "]";
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    /** Tên vai trò theo MeetingPersonal.type. */
    public static String roleLabel(int type) {
        return switch (type) {
            case 1 -> "Chủ trì";
            case 2 -> "Đồng chủ trì";
            case 3 -> "Thư ký";
            default -> "Thành viên";
        };
    }

    public static String personLine(Participant p) {
        return "- " + p.userName() + ": " + p.fullName() + (p.role() > 0 ? " — " + roleLabel(p.role()) : "") + "\n";
    }

    /** Tài liệu đính kèm đã trích văn bản. */
    public record Doc(String name, String text) {
    }

    public record Prompt(String system, String user, Map<String, String> codeToSegment) {
    }

    public static Prompt build(String meetingName, LocalDateTime startUtc, List<Participant> participants,
                               List<MeetingTranscriptSegment> segments) {
        return build(meetingName, startUtc, participants, segments, List.of());
    }

    public static Prompt build(String meetingName, LocalDateTime startUtc, List<Participant> participants,
                               List<MeetingTranscriptSegment> segments, List<Doc> docs) {
        Map<String, String> codes = new LinkedHashMap<>();
        StringBuilder transcript = new StringBuilder();
        LocalDateTime base = startUtc != null ? startUtc : (segments.isEmpty() ? null : segments.get(0).getSpokenAt());
        boolean truncated = false;
        for (int i = 0; i < segments.size(); i++) {
            MeetingTranscriptSegment s = segments.get(i);
            String code = "S" + (i + 1);
            String line = "[" + code + " " + offset(base, s.getSpokenAt()) + "] "
                    + (s.getSpeakerName() == null || s.getSpeakerName().isBlank() ? "Người tham gia" : s.getSpeakerName())
                    + speakerTag(participants, s.getSpeakerUserName(), s.getSpeakerName())
                    + ": " + s.getText().replace('\n', ' ') + "\n";
            if (transcript.length() + line.length() > MAX_TRANSCRIPT_CHARS) {
                truncated = true;
                break;
            }
            codes.put(code, s.getId());
            transcript.append(line);
        }

        StringBuilder people = new StringBuilder();
        participants.forEach(p -> people.append(personLine(p)));

        String system = """
                Bạn là thư ký cuộc họp chuyên nghiệp. Nhiệm vụ: lập biên bản từ transcript được cung cấp.
                QUY TẮC BẮT BUỘC:
                1. Chỉ dùng thông tin có trong transcript và tài liệu đính kèm (nếu có). Không suy đoán, không bịa thêm.
                   Tài liệu đính kèm chỉ để hiểu ngữ cảnh, thuật ngữ, số liệu; quyết định và việc cần làm phải dựa trên điều được NÓI trong transcript.
                2. Mỗi quyết định và việc cần làm phải có "sources": danh sách mã đoạn transcript (dạng "S12") nơi thông tin đó được nói ra.
                3. Không có quyết định / việc cần làm nào thì trả mảng rỗng.
                4. "assignee": chỉ ghi tên đăng nhập (userName) trong danh sách thành viên khi transcript nêu rõ người phụ trách; không rõ thì null.
                5. "due_date": dạng YYYY-MM-DD khi transcript nêu rõ hạn; không rõ thì null.
                6. Viết tiếng Việt, ngắn gọn, khách quan.
                7. """ + ROLE_RULES + """
                8. Trả về DUY NHẤT một đối tượng JSON hợp lệ, không có chữ nào khác, theo mẫu:
                {"summary":"...","summary_sources":["S1"],"decisions":[{"text":"...","sources":["S3"]}],"action_items":[{"text":"...","assignee":null,"due_date":null,"sources":["S5"]}]}
                """;
        String user = "CUỘC HỌP: " + meetingName + "\n"
                + (startUtc != null ? "BẮT ĐẦU: " + vn(startUtc) + " (giờ Việt Nam)\n" : "")
                + "THÀNH VIÊN (userName: họ tên):\n" + people
                + docsSection(docs)
                + "\nTRANSCRIPT" + (truncated ? " (đã cắt bớt phần cuối do quá dài)" : "") + ":\n"
                + (transcript.length() == 0 ? "(Không có transcript.)\n" : transcript);
        return new Prompt(system, user, codes);
    }

    private static String docsSection(List<Doc> docs) {
        if (docs == null || docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nTÀI LIỆU ĐÍNH KÈM (chỉ để tham khảo ngữ cảnh):\n");
        int left = MAX_DOCS_CHARS;
        for (Doc d : docs) {
            if (left <= 0) break;
            String t = d.text().length() > Math.min(MAX_DOC_CHARS, left) ? d.text().substring(0, Math.min(MAX_DOC_CHARS, left)) + "…" : d.text();
            sb.append("--- ").append(d.name()).append(" ---\n").append(t).append('\n');
            left -= t.length();
        }
        return sb.toString();
    }

    /** Đọc JSON của LLM thành nội dung biên bản; mã nguồn không hợp lệ bị bỏ. Ném IllegalStateException nếu không đọc được. */
    public static MinutesContent parse(String raw, Map<String, String> codeToSegment, List<Participant> participants,
                                       ObjectMapper mapper) {
        JsonNode root = readJson(raw, mapper);
        Map<String, Participant> byUser = new HashMap<>();
        Map<String, Participant> byName = new HashMap<>();
        participants.forEach(p -> {
            byUser.put(p.userName().toLowerCase(Locale.ROOT), p);
            if (p.fullName() != null) byName.put(p.fullName().toLowerCase(Locale.ROOT).trim(), p);
        });

        List<MinutesContent.Decision> decisions = new ArrayList<>();
        for (JsonNode d : iter(root.path("decisions"))) {
            String text = d.path("text").asText("").trim();
            if (!text.isEmpty()) decisions.add(new MinutesContent.Decision(newId(), text, sources(d.path("sources"), codeToSegment)));
        }
        List<MinutesContent.ActionItem> actions = new ArrayList<>();
        for (JsonNode a : iter(root.path("action_items"))) {
            String text = a.path("text").asText("").trim();
            if (text.isEmpty()) continue;
            String rawAssignee = a.path("assignee").isNull() ? null : a.path("assignee").asText("").trim();
            Participant who = rawAssignee == null || rawAssignee.isEmpty() ? null
                    : byUser.getOrDefault(rawAssignee.toLowerCase(Locale.ROOT), byName.get(rawAssignee.toLowerCase(Locale.ROOT)));
            String due = a.path("due_date").isNull() ? null : a.path("due_date").asText("").trim();
            actions.add(new MinutesContent.ActionItem(newId(), text,
                    who == null ? null : who.userName(), who == null ? null : who.fullName(),
                    validDate(due), sources(a.path("sources"), codeToSegment), true, null));
        }
        return new MinutesContent(root.path("summary").asText("").trim(),
                sources(root.path("summary_sources"), codeToSegment), decisions, actions);
    }

    /** Cảnh báo chống bịa đặt: mục thiếu nguồn. */
    public static List<String> warnings(MinutesContent c) {
        List<String> w = new ArrayList<>();
        for (int i = 0; i < c.decisions().size(); i++) {
            if (c.decisions().get(i).sources().isEmpty()) w.add("Quyết định " + (i + 1) + " chưa có đoạn transcript nguồn");
        }
        for (int i = 0; i < c.actions().size(); i++) {
            if (c.actions().get(i).sources().isEmpty()) w.add("Việc cần làm " + (i + 1) + " chưa có đoạn transcript nguồn");
        }
        return w;
    }

    public static JsonNode readJson(String raw, ObjectMapper mapper) {
        if (raw == null) throw new IllegalStateException("AI không trả về nội dung.");
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException("AI không trả về JSON hợp lệ.");
        try {
            return mapper.readTree(s.substring(start, end + 1));
        } catch (Exception e) {
            throw new IllegalStateException("AI trả về JSON lỗi: " + e.getMessage());
        }
    }

    private static List<String> sources(JsonNode node, Map<String, String> codes) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : iter(node)) {
            String code = n.asText("").trim().toUpperCase(Locale.ROOT).replace("[", "").replace("]", "");
            if (!code.startsWith("S")) code = "S" + code;
            String seg = codes.get(code);
            if (seg != null && !out.contains(seg)) out.add(seg);
        }
        return out;
    }

    private static Iterable<JsonNode> iter(JsonNode node) {
        List<JsonNode> list = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(list::add);
        return list;
    }

    private static String validDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s).toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String offset(LocalDateTime base, LocalDateTime at) {
        if (base == null || at == null) return "00:00";
        long sec = Math.max(0, java.time.Duration.between(base, at).getSeconds());
        long h = sec / 3600;
        return h > 0 ? String.format("%d:%02d:%02d", h, (sec % 3600) / 60, sec % 60) : String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private static String vn(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE).format(DATE);
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
