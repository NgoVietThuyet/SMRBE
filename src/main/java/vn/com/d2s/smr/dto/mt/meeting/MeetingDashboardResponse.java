package vn.com.d2s.smr.dto.mt.meeting;

import java.util.List;

public record MeetingDashboardResponse(
        int upcoming,
        int ongoing,
        int ended,
        int cancelled,
        List<MeetingListItemResponse> nextMeetings
) {
}
