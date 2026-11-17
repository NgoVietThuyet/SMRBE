package vn.com.d2s.smr.service.mt.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.CleanTurn;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.Glossary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bước 1b: làm sạch transcript bằng LLM, dựa vào từ điển từ khóa gốc: sửa từ nghe sai, bỏ từ thừa, gộp câu ngắt của cùng một người.
 * Chạy theo lô; mỗi lượt lời sau làm sạch giữ danh sách Id đoạn gốc đã gộp. Lô nào LLM lỗi / trả sai thì dùng nguyên văn gốc
 * của các đoạn chưa xử lý được — không bao giờ mất lời nói.
 */
public final class TranscriptCleaner {

    private static final Logger log = LoggerFactory.getLogger(TranscriptCleaner.class);
    static final int BATCH_CHARS = 5_000;
    static final int BATCH_MAX_SEGMENTS = 50;

    private static final String SYSTEM = """
            Bạn là biên tập viên biên bản họp. Nhiệm vụ: làm sạch transcript do máy nhận dạng giọng nói tạo ra (hay nghe sai từ, ngắt câu lủng củng).
            QUY TẮC BẮT BUỘC:
            1. Sửa các từ nghe sai theo DANH SÁCH TỪ KHÓA GỐC (tên người, tên dự án, thuật ngữ, con số). Chỉ sửa khi rõ ràng là nghe nhầm một từ trong danh sách.
            2. Xóa các từ thừa không mang nghĩa (à, ừm, ờ, ừ thì, kiểu như, thì là, ...).
            3. Gộp các câu bị ngắt quãng liên tiếp của CÙNG MỘT người thành câu hoàn chỉnh. Không gộp lời của hai người khác nhau.
            4. KHÔNG thêm thông tin mới, không tóm tắt, không đổi ý nghĩa, không đổi con số hay ngày tháng (trừ khi sửa đúng theo danh sách từ khóa).
            5. Mỗi mã [S..] trong phần TRANSCRIPT phải xuất hiện đúng MỘT lần, theo thứ tự, trong trường "ids" của một lượt lời.
            6. Phần NGỮ CẢNH chỉ để hiểu, KHÔNG đưa vào kết quả.
            7. Trả về DUY NHẤT một đối tượng JSON hợp lệ, không có chữ nào khác: {"turns":[{"ids":["S1","S2"],"text":"..."}]}
            """;

    private TranscriptCleaner() {
    }

    public static List<CleanTurn> clean(List<MeetingTranscriptSegment> segments, Glossary glossary, LlmClientService llm, ObjectMapper mapper) {
        List<CleanTurn> result = new ArrayList<>();
        int i = 0;
        while (i < segments.size()) {
            int chars = 0;
            int j = i;
            while (j < segments.size() && j - i < BATCH_MAX_SEGMENTS && (j == i || chars + len(segments.get(j)) <= BATCH_CHARS)) {
                chars += len(segments.get(j));
                j++;
            }
            List<MeetingTranscriptSegment> batch = segments.subList(i, j);
            List<String> context = new ArrayList<>();
            for (int k = Math.max(0, result.size() - 2); k < result.size(); k++) {
                context.add(result.get(k).speaker() + ": " + result.get(k).text());
            }
            result.addAll(cleanBatch(batch, i, glossary, context, llm, mapper));
            i = j;
        }
        return result;
    }

    private static int len(MeetingTranscriptSegment s) {
        return s.getText() == null ? 0 : s.getText().length() + 40;
    }

    /** Làm sạch một lô; globalOffset = chỉ số đoạn đầu lô trong toàn transcript (để mã S liên tục). */
    static List<CleanTurn> cleanBatch(List<MeetingTranscriptSegment> batch, int globalOffset, Glossary glossary,
                                      List<String> context, LlmClientService llm, ObjectMapper mapper) {
        Map<String, Integer> codeToIndex = new HashMap<>();
        StringBuilder transcript = new StringBuilder();
        for (int k = 0; k < batch.size(); k++) {
            String code = "S" + (globalOffset + k + 1);
            codeToIndex.put(code, k);
            MeetingTranscriptSegment s = batch.get(k);
            transcript.append('[').append(code).append("] ").append(speaker(s)).append(": ")
                    .append(s.getText().replace('\n', ' ')).append('\n');
        }
        String user = "DANH SÁCH TỪ KHÓA GỐC:\n" + (glossary == null ? "(Không có.)\n" : glossary.toPromptText())
                + (context.isEmpty() ? "" : "\nNGỮ CẢNH (đoạn ngay trước, không sửa, không đưa vào kết quả):\n" + String.join("\n", context) + "\n")
                + "\nTRANSCRIPT:\n" + transcript;

        List<Object[]> parsed = new ArrayList<>(); // {Integer minIndex, CleanTurn}
        Set<Integer> covered = new HashSet<>();
        try {
            String raw = llm.generate(SYSTEM, user, 0.1, 4000);
            if (raw == null || raw.isBlank()) throw new IllegalStateException("LLM không trả lời");
            JsonNode root = MinutesAiGenerator.readJson(raw, mapper);
            for (JsonNode t : root.path("turns")) {
                List<Integer> idx = new ArrayList<>();
                boolean ok = true;
                for (JsonNode idNode : t.path("ids")) {
                    Integer k = codeToIndex.get(idNode.asText("").trim().toUpperCase().replace("[", "").replace("]", ""));
                    if (k == null || covered.contains(k) || idx.contains(k)) { ok = false; break; }
                    idx.add(k);
                }
                String text = t.path("text").asText("").trim();
                if (!ok || idx.isEmpty() || text.isEmpty()) continue;
                idx.sort(Integer::compare);
                String who = speakerKey(batch.get(idx.get(0)));
                // Không cho gộp lời của nhiều người: bỏ lượt này, các đoạn của nó sẽ rơi về nguyên văn.
                if (idx.stream().anyMatch(k -> !speakerKey(batch.get(k)).equals(who))) continue;
                List<String> ids = new ArrayList<>();
                StringBuilder rawText = new StringBuilder();
                for (int k : idx) {
                    ids.add(batch.get(k).getId());
                    rawText.append(batch.get(k).getText().trim()).append(' ');
                    covered.add(k);
                }
                parsed.add(new Object[]{idx.get(0), new CleanTurn(ids, speaker(batch.get(idx.get(0))), text,
                        rawText.toString().trim(), batch.get(idx.get(0)).getSpokenAt())});
            }
        } catch (RuntimeException e) {
            log.warn("Làm sạch transcript lô lỗi, dùng nguyên văn: {}", e.getMessage());
        }
        // Đoạn chưa được xử lý → giữ nguyên văn gốc.
        for (int k = 0; k < batch.size(); k++) {
            if (covered.contains(k)) continue;
            MeetingTranscriptSegment s = batch.get(k);
            String text = s.getText() == null ? "" : s.getText().trim();
            if (text.isEmpty()) continue;
            parsed.add(new Object[]{k, new CleanTurn(List.of(s.getId()), speaker(s), text, text, s.getSpokenAt())});
        }
        parsed.sort(Comparator.comparingInt(o -> (Integer) o[0]));
        List<CleanTurn> out = new ArrayList<>();
        for (Object[] o : parsed) out.add((CleanTurn) o[1]);
        return out;
    }

    private static String speaker(MeetingTranscriptSegment s) {
        return s.getSpeakerName() == null || s.getSpeakerName().isBlank() ? "Người tham gia" : s.getSpeakerName();
    }

    private static String speakerKey(MeetingTranscriptSegment s) {
        String u = s.getSpeakerUserName();
        return (u != null && !u.isBlank() ? u : speaker(s)).toLowerCase();
    }
}
