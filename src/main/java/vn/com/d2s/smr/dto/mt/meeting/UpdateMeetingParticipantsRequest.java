package vn.com.d2s.smr.dto.mt.meeting;

import java.util.List;

public record UpdateMeetingParticipantsRequest(
        List<MeetingParticipantInputDto> participants,
        List<String> participantUserNames
) {
}
