package vn.com.d2s.smr.dto.rp;

import java.time.LocalDate;

/** Thống kê công việc tạo trong khoảng [from, to] (P5). Quá hạn = chưa hoàn thành và DueDate đã qua. */
public record TaskReportResponse(
        LocalDate from,
        LocalDate to,
        int total,
        int notStarted,
        int inProgress,
        int completed,
        int overdue
) {
}
