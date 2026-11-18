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
        String hostName,
        /** Người tạo (hoặc quản trị viên) và cuộc họp không đang diễn ra → được xóa kèm biên bản, bản ghi. */
        @JsonProperty("canDelete") boolean canDelete
) {
    public MeetingListItemResponse(String id, String name, String description, LocalDateTime expectedStartTime,
                                   LocalDateTime expectedEndTime, int status, int visibility, String roomCode,
                                   String joinUrl, int participantCount, boolean host, String hostName) {
        this(id, name, description, expectedStartTime, expectedEndTime, status, visibility, roomCode, joinUrl,
                participantCount, host, hostName, false);
    }
}
