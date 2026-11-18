package vn.com.d2s.smr.dto.mt.poll;

import java.time.LocalDateTime;
import java.util.List;

/** Biểu quyết kèm kết quả cho một người xem. voters: theo từng phương án, null nếu ẩn danh. */
public record PollResponse(
        String id,
        String meetingId,
        String question,
        List<String> options,
        boolean anonymous,
        boolean closed,
        String createdBy,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        List<Integer> counts,
        int total,
        Integer myVote,
        List<List<String>> voters,
        int winner,
        boolean canClose
) {
}
