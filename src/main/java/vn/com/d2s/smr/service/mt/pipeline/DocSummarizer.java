package vn.com.d2s.smr.service.mt.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.DocDigest;

import java.util.ArrayList;
import java.util.List;

/** Bước 2 (chuẩn bị): tóm tắt từng tài liệu để có đủ ngữ cảnh mà không phải gửi nguyên văn dài. Lỗi LLM thì dùng đoạn đầu tài liệu. */
public final class DocSummarizer {

    private static final Logger log = LoggerFactory.getLogger(DocSummarizer.class);
    static final int MAX_INPUT_CHARS = 9_000;
    static final int FALLBACK_CHARS = 1_200;

    private DocSummarizer() {
    }

    public static List<DocDigest> summarize(List<MinutesAiGenerator.Doc> docs, LlmClientService llm) {
        List<DocDigest> out = new ArrayList<>();
        if (docs == null) return out;
        for (MinutesAiGenerator.Doc d : docs) {
            String text = d.text().replace('\f', '\n');
            String input = text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
            String summary = null;
            try {
                summary = llm.generate("""
                        Bạn tóm tắt tài liệu để làm ngữ cảnh cho thư ký cuộc họp. Viết 5-10 gạch đầu dòng tiếng Việt: mục đích tài liệu,
                        các hạng mục chính, tên riêng và con số quan trọng (GIỮ NGUYÊN số liệu, tên, đơn vị). Không suy đoán, không thêm ý.
                        Chỉ trả về nội dung tóm tắt.
                        """, "TÀI LIỆU: " + d.name() + "\n" + input, 0.2, 700);
            } catch (RuntimeException e) {
                log.warn("Tóm tắt tài liệu {} lỗi: {}", d.name(), e.getMessage());
            }
            if (summary == null || summary.isBlank()) {
                summary = text.length() > FALLBACK_CHARS ? text.substring(0, FALLBACK_CHARS) + "…" : text;
            }
            out.add(new DocDigest(d.name(), summary.trim()));
        }
        return out;
    }
}
