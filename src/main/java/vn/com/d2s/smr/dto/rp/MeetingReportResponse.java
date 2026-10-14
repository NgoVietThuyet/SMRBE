package vn.com.d2s.smr.dto.rp;

import java.time.LocalDate;
import java.util.List;

/** Thống kê cuộc họp toàn hệ thống trong khoảng [from, to] (P5). */
public record MeetingReportResponse(
        LocalDate from,
        LocalDate to,
        int meetingCount,
        int draftCount,
        int upcomingCount,
        int ongoingCount,
        int endedCount,
        int cancelledCount,
        /** Tổng giờ họp thực tế (StartDate → EndDate), làm tròn 1 chữ số. */
        double totalHours,
        /** Số người được mời (không trùng). */
        int participantCount,
        /** Số người đã vào phòng ít nhất một lần (không trùng). */
        int attendedCount,
        List<WeekStat> byWeek,
        List<OrganizationStat> byOrganization
) {
    /** weekStart = thứ Hai của tuần. */
    public record WeekStat(LocalDate weekStart, int meetingCount, double hours) {
    }

    /** Phòng ban của chủ trì cuộc họp. */
    public record OrganizationStat(String organizationId, String organizationName, int meetingCount, double hours,
                                   int participantCount) {
    }
}
