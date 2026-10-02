package vn.com.d2s.smr.dto.mt.meeting;

import java.util.List;

public record QuickMeetingRequest(
        String name,
        List<String> participantUserNames
) {
}
