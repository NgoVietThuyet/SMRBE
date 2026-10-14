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

    public static final String PROMPT_VERSION = "minutes-v1";
    /** Giới hạn độ dài transcript gửi LLM (ký tự) — dài hơn thì cắt bớt phần cuối, có ghi chú trong prompt. */
    static final int MAX_TRANSCRIPT_CHARS = 24_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private MinutesAiGenerator() {
    }

    public record Participant(String userName, String fullName) {
    }

    public record Prompt(String system, String user, Map<String, String> codeToSegment) {
    }

    public static Prompt build(String meetingName, LocalDateTime startUtc, List<Participant> participants,
                               List<MeetingTranscriptSegment> segments) {
        Map<String, String> codes = new LinkedHashMap<>();
        StringBuilder transcript = new StringBuilder();
        LocalDateTime base = startUtc != null ? startUtc : (segments.isEmpty() ? null : segments.get(0).getSpokenAt());
        boolean truncated = false;
        for (int i = 0; i < segments.size(); i++) {
            MeetingTranscriptSegment s = segments.get(i);
            String code = "S" + (i + 1);
            String line = "[" + code + " " + offset(base, s.getSpokenAt()) + "] "
                    + (s.getSpeakerName() == null || s.getSpeakerName().isBlank() ? "Người tham gia" : s.getSpeakerName())
                    + ": " + s.getText().replace('\n', ' ') + "\n";
            if (transcript.length() + line.length() > MAX_TRANSCRIPT_CHARS) {
                truncated = true;
                break;
            }
            codes.put(code, s.getId());
            transcript.append(line);
        }

        StringBuilder people = new StringBuilder();
        participants.forEach(p -> people.append("- ").append(p.userName()).append(": ").append(p.fullName()).append('\n'));

        String system = """
                Bạn là thư ký cuộc họp chuyên nghiệp. Nhiệm vụ: lập biên bản từ transcript được cung cấp.
                QUY TẮC BẮT BUỘC:
                1. Chỉ dùng thông tin có trong transcript. Không suy đoán, không bịa thêm.
                2. Mỗi quyết định và việc cần làm phải có "sources": danh sách mã đoạn transcript (dạng "S12") nơi thông tin đó được nói ra.
                3. Không có quyết định / việc cần làm nào thì trả mảng rỗng.
                4. "assignee": chỉ ghi tên đăng nhập (userName) trong danh sách thành viên khi transcript nêu rõ người phụ trách; không rõ thì null.
                5. "due_date": dạng YYYY-MM-DD khi transcript nêu rõ hạn; không rõ thì null.
                6. Viết tiếng Việt, ngắn gọn, khách quan.
                7. Trả về DUY NHẤT một đối tượng JSON hợp lệ, không có chữ nào khác, theo mẫu:
                {"summary":"...","summary_sources":["S1"],"decisions":[{"text":"...","sources":["S3"]}],"action_items":[{"text":"...","assignee":null,"due_date":null,"sources":["S5"]}]}
                """;
        String user = "CUỘC HỌP: " + meetingName + "\n"
                + (startUtc != null ? "BẮT ĐẦU: " + vn(startUtc) + " (giờ Việt Nam)\n" : "")
                + "THÀNH VIÊN (userName: họ tên):\n" + people
                + "\nTRANSCRIPT" + (truncated ? " (đã cắt bớt phần cuối do quá dài)" : "") + ":\n" + transcript;
        return new Prompt(system, user, codes);
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

    static JsonNode readJson(String raw, ObjectMapper mapper) {
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
