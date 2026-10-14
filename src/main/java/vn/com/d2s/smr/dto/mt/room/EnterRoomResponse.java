package vn.com.d2s.smr.dto.mt.room;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kết quả xin vào phòng. state: ADMITTED · LOBBY · REJECTED · WAIT_HOST · NOT_READY · ENDED · CANCELLED.
 * requestId có khi đang chờ duyệt.
 */
public record EnterRoomResponse(
        String state,
        String requestId,
        String message,
        @JsonProperty("isModerator") boolean moderator
) {
}
