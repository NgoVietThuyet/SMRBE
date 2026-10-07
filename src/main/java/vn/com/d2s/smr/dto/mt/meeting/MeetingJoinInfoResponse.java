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
        @JsonProperty("isGuest") boolean guest,
        @JsonProperty("domain") String domain,
        @JsonProperty("isModerator") boolean moderator,
        @JsonProperty("startWithAudioMuted") boolean startWithAudioMuted,
        @JsonProperty("startWithVideoMuted") boolean startWithVideoMuted,
        @JsonProperty("externalApiUrl") String externalApiUrl
) {
    public MeetingJoinInfoResponse(
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
            boolean host,
            boolean guest
    ) {
        this(
                meetingId,
                name,
                roomCode,
                joinUrl,
                status,
                expectedStartTime,
                expectedEndTime,
                jitsiDomain != null ? jitsiDomain : "meet.d2s.vn",
                roomName,
                displayName,
                host,
                guest,
                jitsiDomain != null ? jitsiDomain : "meet.d2s.vn",
                host,
                false,
                false,
                "https://" + (jitsiDomain != null ? jitsiDomain : "meet.d2s.vn") + "/external_api.js"
        );
    }
}
