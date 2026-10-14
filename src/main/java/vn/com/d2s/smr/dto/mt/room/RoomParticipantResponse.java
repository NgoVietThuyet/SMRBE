package vn.com.d2s.smr.dto.mt.room;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomParticipantResponse(
        String userName,
        String fullName,
        int role,
        @JsonProperty("isModerator") boolean moderator,
        @JsonProperty("isGuest") boolean guest,
        @JsonProperty("isOnline") boolean online
) {
}
