package vn.com.d2s.smr.dto.mt.room;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Thời lượng tham dự của một thành viên (thời gian UTC). */
public record AttendanceSummaryResponse(
        String userName,
        String fullName,
        int role,
        @JsonProperty("isGuest") boolean guest,
        LocalDateTime firstJoinAt,
        LocalDateTime lastLeaveAt,
        int joinCount,
        long totalSeconds
) {
}
