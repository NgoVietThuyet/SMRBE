package vn.com.d2s.smr.dto.nt;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Một thông báo của người đang đăng nhập. Thời gian trả dạng Instant (ISO có 'Z'). */
public record NotificationItemResponse(
        String id,
        String type,
        int severity,
        String title,
        String body,
        String resourceType,
        String resourceId,
        String meetingId,
        String actorUserName,
        String actorFullName,
        JsonNode data,
        boolean isRead,
        Instant createDate
) {
}
