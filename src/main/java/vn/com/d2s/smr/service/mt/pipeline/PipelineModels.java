package vn.com.d2s.smr.service.mt.pipeline;

import java.time.LocalDateTime;
import java.util.List;

/** Các bản ghi dữ liệu giữa các bước của pipeline sau họp. */
public final class PipelineModels {

    private PipelineModels() {
    }

    /** Từ điển từ khóa gốc: tên người, tên dự án / thuật ngữ / con số quan trọng lấy từ tài liệu. */
    public record Glossary(List<String> names, List<String> terms) {
        public boolean isEmpty() {
            return names.isEmpty() && terms.isEmpty();
        }

        public String toPromptText() {
            StringBuilder sb = new StringBuilder();
            if (!names.isEmpty()) sb.append("Tên người tham dự: ").append(String.join("; ", names)).append('\n');
            if (!terms.isEmpty()) sb.append("Tên dự án, thuật ngữ, con số trong tài liệu: ").append(String.join("; ", terms)).append('\n');
            return sb.length() == 0 ? "(Không có.)\n" : sb.toString();
        }
    }

    /** Tóm tắt một tài liệu đính kèm. */
    public record DocDigest(String name, String summary) {
    }

    /**
     * Một lượt lời đã làm sạch (có thể gộp nhiều đoạn gốc của cùng một người). segmentIds = Id các đoạn transcript gốc đã gộp,
     * để dẫn nguồn trong biên bản vẫn trỏ đúng đoạn gốc. raw = văn bản gốc nối lại (để đối chiếu).
     */
    public record CleanTurn(List<String> segmentIds, String speaker, String text, String raw, LocalDateTime at) {
    }
}
