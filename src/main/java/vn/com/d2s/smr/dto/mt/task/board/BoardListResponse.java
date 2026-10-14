package vn.com.d2s.smr.dto.mt.task.board;

import java.util.List;

/** Một cột (đầu việc cấp 1). */
public record BoardListResponse(
        String id,
        String title,
        int sortOrder,
        List<BoardCardResponse> cards
) {
}
