package vn.com.d2s.smr.dto.mt.poll;

import java.util.List;

/**
 * Kết quả tổng của một biểu quyết, dạng đưa vào hồ sơ chốt / AI: không có tên người bỏ phiếu
 * (kể cả biểu quyết công khai), chỉ số phiếu từng phương án.
 */
public record PollSummary(String question, List<String> options, List<Integer> counts, int total, boolean closed) {

    public PollSummary {
        options = options == null ? List.of() : options;
        counts = counts == null ? List.of() : counts;
    }

    /** Chỉ số phương án dẫn đầu; -1 nếu chưa ai bỏ phiếu hoặc có từ hai phương án bằng phiếu cao nhất. */
    public int winner() {
        int best = -1;
        int max = 0;
        boolean tie = false;
        for (int i = 0; i < counts.size(); i++) {
            int c = counts.get(i);
            if (c > max) {
                max = c;
                best = i;
                tie = false;
            } else if (c == max && c > 0) {
                tie = true;
            }
        }
        return tie ? -1 : best;
    }

    /** Đoạn văn bản đưa cho AI. */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder("- Câu hỏi: ").append(question).append(closed ? "" : " (chưa đóng)").append('\n');
        for (int i = 0; i < options.size(); i++) {
            sb.append("    · ").append(options.get(i)).append(": ").append(i < counts.size() ? counts.get(i) : 0).append(" phiếu\n");
        }
        int w = winner();
        sb.append("    Tổng ").append(total).append(" phiếu. ")
                .append(total == 0 ? "Không ai bỏ phiếu."
                        : w >= 0 ? "Phương án nhiều phiếu nhất: " + options.get(w) + "." : "Hòa phiếu, chưa có phương án thắng.")
                .append('\n');
        return sb.toString();
    }
}
