package vn.com.d2s.smr.dto.mt.room;

import java.time.LocalDateTime;

/**
 * Thông tin tối thiểu cho màn /join/:roomCode (chưa đăng nhập).
 * guestAllowed = cuộc họp Công khai + bật "Cho phép khách" + đang/sắp diễn ra.
 * reason: NOT_FOUND · CANCELLED · ENDED · GUEST_NOT_ALLOWED (null khi vào được).
 */
public record PublicMeetingResponse(
        String meetingId,
        String name,
        int status,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        String hostName,
        boolean guestAllowed,
        String reason
) {
    public static PublicMeetingResponse notFound() {
        return new PublicMeetingResponse(null, null, -1, null, null, null, false, "NOT_FOUND");
    }
}
