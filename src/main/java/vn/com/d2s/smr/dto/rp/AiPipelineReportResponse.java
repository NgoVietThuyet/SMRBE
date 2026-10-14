package vn.com.d2s.smr.dto.rp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Thống kê job AI nền (P5, từ SyBackgroundJobs) trong khoảng [from, to] theo ngày tạo job.
 * successRate = xong / (xong + lỗi), %. waitSeconds = chờ trong hàng đợi (tạo → bắt đầu); runSeconds = chạy (bắt đầu → xong).
 */
public record AiPipelineReportResponse(
        LocalDate from,
        LocalDate to,
        int total,
        int pending,
        int running,
        int done,
        int failed,
        double successRate,
        double avgWaitSeconds,
        double avgRunSeconds,
        double p95RunSeconds,
        List<TypeStat> byType,
        List<Failure> recentFailures
) {
    public record TypeStat(String type, String label, int total, int done, int failed, double avgRunSeconds) {
    }

    public record Failure(String id, String type, String label, String meetingId, String meetingName, String error,
                          String createdBy, LocalDateTime createdAt) {
    }
}
