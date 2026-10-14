package vn.com.d2s.smr.dto.mt.task.board;

import java.time.LocalDateTime;
import java.util.List;

/** Số lượng cho sidebar trang Công việc (P12) + các cuộc họp có công việc. */
public record TaskGroupsResponse(
        int today,
        int overdue,
        int mine,
        int assigned,
        int shared,
        int publicCount,
        List<MeetingGroup> meetings
) {
    public record MeetingGroup(String meetingId, String name, LocalDateTime startTime, int count) {
    }
}
