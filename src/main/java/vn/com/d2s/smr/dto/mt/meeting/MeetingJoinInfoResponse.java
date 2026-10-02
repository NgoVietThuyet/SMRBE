package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record MeetingJoinInfoResponse(
        String meetingId,
        String name,
        String roomCode,
        String joinUrl,
        int status,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        String jitsiDomain,
        String roomName,
        String displayName,
        @JsonProperty("isHost") boolean host,
        @JsonProperty("isGuest") boolean guest
) {
}
