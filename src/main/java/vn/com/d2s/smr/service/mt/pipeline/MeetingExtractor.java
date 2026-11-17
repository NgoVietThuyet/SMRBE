package vn.com.d2s.smr.service.mt.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.CleanTurn;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.DocDigest;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bước 2: trích diễn biến, quyết định và công việc. Kỹ thuật "diễn biến trước, chốt sau": JSON ép AI viết
 * "dien_bien_thao_luan" (ai đề xuất, ai phản biện, cuối cùng chốt gì) TRƯỚC khi điền "quyet_dinh_cuoi_cung" và "action_items",
 * nên ý kiến đã bị bác bỏ không lọt vào quyết định. Không cắt vụn: transcript gửi trong một lần gọi; quá dài mới chia phần rồi hợp nhất.
 */
public final class MeetingExtractor {

    private static final Logger log = LoggerFactory.getLogger(MeetingExtractor.class);
    public static final String PROMPT_VERSION = "minutes-v3";
    static final int WINDOW_CHARS = 20_000;

    static final String SYSTEM = """
            Bạn là thư ký cuộc họp chuyên nghiệp. Lập biên bản từ transcript đã được làm sạch.
            QUY TẮC BẮT BUỘC:
            1. Chỉ dùng thông tin trong transcript và phần tóm tắt tài liệu. Tài liệu chỉ để hiểu ngữ cảnh, thuật ngữ, số liệu;
               quyết định và việc cần làm phải dựa trên điều được NÓI trong cuộc họp. Không suy đoán, không bịa thêm.
            2. DIỄN BIẾN TRƯỚC, CHỐT SAU: trước hết viết "dien_bien_thao_luan" theo từng chủ đề, kể tuần tự ai đề xuất gì, ai phản biện,
               có ai đổi ý không, cuối cùng chốt ra sao. Chỉ SAU KHI viết diễn biến mới điền "quyet_dinh_cuoi_cung" và "action_items".
            3. "quyet_dinh_cuoi_cung" CHỈ gồm kết luận cuối cùng của từng chủ đề. Bỏ các ý kiến đã bị bác bỏ hoặc bị thay thế về sau
               trong cuộc họp, cùng các câu nói đùa hay bàn lùi.
            4. "action_items" chỉ gồm việc được giao hoặc nhận rõ ràng và còn hiệu lực sau diễn biến.
            5. "tom_tat": tóm tắt chi tiết 2-4 đoạn (khoảng 150-300 từ): bối cảnh, các chủ đề chính, kết quả; ghi số liệu cụ thể nếu có.
            6. Mỗi mục có "sources": danh sách mã lượt lời (dạng "T12") làm căn cứ.
            7. "assignee": chỉ ghi tên đăng nhập (userName) trong danh sách thành viên khi rõ người phụ trách; không rõ thì null.
               "due_date": YYYY-MM-DD khi nêu rõ hạn; không rõ thì null.
            8. Viết tiếng Việt, khách quan. Không có quyết định hoặc việc cần làm thì trả mảng rỗng.
            9. Trả về DUY NHẤT một đối tượng JSON hợp lệ, không có chữ nào khác, ĐÚNG THỨ TỰ TRƯỜNG sau:
            {"dien_bien_thao_luan":[{"chu_de":"...","dien_bien":"...","sources":["T1"]}],"tom_tat":"...","tom_tat_sources":["T1"],"quyet_dinh_cuoi_cung":[{"text":"...","sources":["T3"]}],"action_items":[{"text":"...","assignee":null,"due_date":null,"sources":["T5"]}]}
            """;

    static final String MERGE_SYSTEM = """
            Bạn hợp nhất kết quả từng PHẦN của CÙNG MỘT cuộc họp thành biên bản cuối cùng.
            QUY TẮC: nếu phần sau thay đổi hoặc bác bỏ một quyết định / việc của phần trước thì CHỈ giữ kết luận cuối cùng; không bịa thêm;
            giữ nguyên mã "sources" (dạng "T12"); gộp các chủ đề trùng nhau; "tom_tat" chi tiết 2-4 đoạn (150-300 từ).
            Trả về DUY NHẤT một đối tượng JSON hợp lệ theo ĐÚNG THỨ TỰ TRƯỜNG:
            {"dien_bien_thao_luan":[{"chu_de":"...","dien_bien":"...","sources":["T1"]}],"tom_tat":"...","tom_tat_sources":["T1"],"quyet_dinh_cuoi_cung":[{"text":"...","sources":["T3"]}],"action_items":[{"text":"...","assignee":null,"due_date":null,"sources":["T5"]}]}
            """;

    private MeetingExtractor() {
    }

    public static MinutesContent extract(String meetingName, LocalDateTime startUtc, List<MinutesAiGenerator.Participant> participants,
                                         List<CleanTurn> turns, List<DocDigest> digests, LlmClientService llm,
                                         ObjectMapper mapper, int singleCallChars) {
        if (turns.isEmpty() && (digests == null || digests.isEmpty())) {
            throw new IllegalStateException("Cuộc họp chưa có transcript hoặc tài liệu để AI tổng hợp.");
        }
        Map<String, List<String>> codeToSegments = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        LocalDateTime base = startUtc != null ? startUtc : (turns.isEmpty() ? null : turns.get(0).at());
        for (int i = 0; i < turns.size(); i++) {
            CleanTurn t = turns.get(i);
            String code = "T" + (i + 1);
            codeToSegments.put(code, t.segmentIds());
            lines.add("[" + code + " " + MinutesAiGenerator.offset(base, t.at()) + "] " + t.speaker() + ": " + t.text().replace('\n', ' '));
        }
        String header = "CUỘC HỌP: " + meetingName + "\n"
                + "THÀNH VIÊN (userName: họ tên):\n" + people(participants)
                + digestsText(digests);

        int total = lines.stream().mapToInt(String::length).sum();
        String json;
        if (total <= singleCallChars) {
            json = call(SYSTEM, header + "\nTRANSCRIPT ĐÃ LÀM SẠCH:\n" + String.join("\n", lines), 3500, llm);
        } else {
            // Họp rất dài: chia phần lớn, mang kết quả các phần trước sang phần sau, cuối cùng hợp nhất.
            List<String> partials = new ArrayList<>();
            List<List<String>> windows = windows(lines);
            for (int w = 0; w < windows.size(); w++) {
                String prior = partials.isEmpty() ? "" : "\nKẾT QUẢ CÁC PHẦN TRƯỚC (để biết đầu đuôi; phần này có thể thay đổi quyết định trước đó, nếu vậy ghi rõ trong dien_bien):\n"
                        + String.join("\n", partials) + "\n";
                partials.add(call(SYSTEM, header + prior + "\nPHẦN " + (w + 1) + "/" + windows.size() + " CỦA TRANSCRIPT ĐÃ LÀM SẠCH:\n"
                        + String.join("\n", windows.get(w)), 3500, llm));
            }
            json = call(MERGE_SYSTEM, header + "\nKẾT QUẢ TỪNG PHẦN:\n" + String.join("\n---\n", partials), 4000, llm);
        }
        return parse(json, codeToSegments, participants, mapper);
    }

    private static List<List<String>> windows(List<String> lines) {
        List<List<String>> out = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        int chars = 0;
        for (String l : lines) {
            if (!cur.isEmpty() && chars + l.length() > WINDOW_CHARS) {
                out.add(cur);
                cur = new ArrayList<>();
                chars = 0;
            }
            cur.add(l);
            chars += l.length();
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    private static String call(String system, String user, int maxTokens, LlmClientService llm) {
        String raw = llm.generate(system, user, 0.2, maxTokens);
        if (raw == null || raw.isBlank()) throw new IllegalStateException("Không kết nối được máy chủ AI. Hãy thử lại sau.");
        return raw;
    }

    private static String people(List<MinutesAiGenerator.Participant> participants) {
        StringBuilder sb = new StringBuilder();
        participants.forEach(p -> sb.append("- ").append(p.userName()).append(": ").append(p.fullName()).append('\n'));
        return sb.toString();
    }

    private static String digestsText(List<DocDigest> digests) {
        if (digests == null || digests.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nTÓM TẮT TÀI LIỆU ĐÍNH KÈM (chỉ để hiểu ngữ cảnh):\n");
        for (DocDigest d : digests) sb.append("--- ").append(d.name()).append(" ---\n").append(d.summary()).append('\n');
        return sb.toString();
    }

    /** Đọc JSON của LLM → nội dung biên bản; mã nguồn T# đổi thành Id đoạn gốc; mã không hợp lệ bị bỏ. */
    static MinutesContent parse(String raw, Map<String, List<String>> codeToSegments, List<MinutesAiGenerator.Participant> participants,
                                ObjectMapper mapper) {
        JsonNode root = MinutesAiGenerator.readJson(raw, mapper);
        Map<String, MinutesAiGenerator.Participant> byUser = new HashMap<>();
        Map<String, MinutesAiGenerator.Participant> byName = new HashMap<>();
        participants.forEach(p -> {
            byUser.put(p.userName().toLowerCase(Locale.ROOT), p);
            if (p.fullName() != null) byName.put(p.fullName().toLowerCase(Locale.ROOT).trim(), p);
        });

        List<MinutesContent.Discussion> discussion = new ArrayList<>();
        for (JsonNode d : arr(root.path("dien_bien_thao_luan"))) {
            String text = d.path("dien_bien").asText("").trim();
            if (text.isEmpty()) continue;
            discussion.add(new MinutesContent.Discussion(d.path("chu_de").asText("").trim(), text, sources(d.path("sources"), codeToSegments)));
        }
        List<MinutesContent.Decision> decisions = new ArrayList<>();
        for (JsonNode d : arr(root.path("quyet_dinh_cuoi_cung"))) {
            String text = d.path("text").asText("").trim();
            if (!text.isEmpty()) decisions.add(new MinutesContent.Decision(newId(), text, sources(d.path("sources"), codeToSegments)));
        }
        List<MinutesContent.ActionItem> actions = new ArrayList<>();
        for (JsonNode a : arr(root.path("action_items"))) {
            String text = a.path("text").asText("").trim();
            if (text.isEmpty()) continue;
            String rawAssignee = a.path("assignee").isNull() ? null : a.path("assignee").asText("").trim();
            MinutesAiGenerator.Participant who = rawAssignee == null || rawAssignee.isEmpty() ? null
                    : byUser.getOrDefault(rawAssignee.toLowerCase(Locale.ROOT), byName.get(rawAssignee.toLowerCase(Locale.ROOT)));
            String due = a.path("due_date").isNull() ? null : a.path("due_date").asText("").trim();
            actions.add(new MinutesContent.ActionItem(newId(), text, who == null ? null : who.userName(), who == null ? null : who.fullName(),
                    validDate(due), sources(a.path("sources"), codeToSegments), true, null));
        }
        String summary = root.path("tom_tat").asText("").trim();
        if (summary.isEmpty() && discussion.isEmpty() && decisions.isEmpty() && actions.isEmpty()) {
            throw new IllegalStateException("AI trả về biên bản rỗng.");
        }
        return new MinutesContent(summary, sources(root.path("tom_tat_sources"), codeToSegments), decisions, actions, discussion);
    }

    private static List<String> sources(JsonNode node, Map<String, List<String>> codes) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr(node)) {
            String code = n.asText("").trim().toUpperCase(Locale.ROOT).replace("[", "").replace("]", "");
            if (!code.startsWith("T")) code = "T" + code;
            List<String> segs = codes.get(code);
            if (segs != null) segs.forEach(s -> { if (!out.contains(s)) out.add(s); });
        }
        return out;
    }

    private static List<JsonNode> arr(JsonNode node) {
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

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
