package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record MeetingParticipantResponse(
        String userName,
        String fullName,
        String email,
        String organizationId,
        String titleCode,
        int role,
        @JsonProperty("isJoined") boolean joined,
        LocalDateTime joinTime
) {
}
