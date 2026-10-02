package vn.com.d2s.smr.dto.mt.task;

public record TaskSummaryResponse(
        int total,
        int mine,
        int overdue,
        int today
) {
}
