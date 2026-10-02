package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record MeetingListItemResponse(
        String id,
        String name,
        String description,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        int status,
        int visibility,
        String roomCode,
        String joinUrl,
        int participantCount,
        @JsonProperty("isHost") boolean host,
        String hostName
) {
}
