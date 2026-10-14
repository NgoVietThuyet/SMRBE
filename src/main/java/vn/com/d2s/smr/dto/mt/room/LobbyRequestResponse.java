package vn.com.d2s.smr.dto.mt.room;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record LobbyRequestResponse(
        String id,
        String userName,
        String displayName,
        @JsonProperty("isGuest") boolean guest,
        LocalDateTime requestedAt
) {
}
