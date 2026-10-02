package vn.com.d2s.smr.dto.mt.task;

import java.util.List;

public record TaskSearchResultResponse(
        List<TaskListItemResponse> items,
        long totalItems,
        int totalPages,
        int page,
        int pageSize,
        TaskSummaryResponse summary
) {
}
