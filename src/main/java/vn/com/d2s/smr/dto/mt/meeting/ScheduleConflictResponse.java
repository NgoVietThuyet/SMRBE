package vn.com.d2s.smr.dto.mt.meeting;

import java.time.LocalDateTime;

/** Một người bị trùng lịch với một cuộc họp khác (đã lên lịch hoặc đang diễn ra). */
public record ScheduleConflictResponse(
        String meetingId,
        String meetingName,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        int status,
        String userName,
        String fullName
) {
}
