package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Kết quả tra cứu cuộc họp theo mã phòng (GET /api/Meeting/by-code/{roomCode}) — dùng cho "Tham gia bằng mã".
 * Không chứa joinUrl hay thông tin Jitsi; khi vào phòng FE gọi join-info như bình thường.
 *
 * @param reason null khi canJoin; ngược lại: NOT_FOUND, NOT_INVITED, CANCELLED, ENDED
 */
public record MeetingCodeLookupResponse(
        String meetingId,
        String name,
        int status,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        String hostName,
        @JsonProperty("isMember") boolean member,
        boolean canJoin,
        String reason
) {
    public static MeetingCodeLookupResponse notFound() {
        return new MeetingCodeLookupResponse(null, null, -1, null, null, null, false, false, "NOT_FOUND");
    }
}
