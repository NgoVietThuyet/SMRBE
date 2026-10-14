package vn.com.d2s.smr.dto.nt;

import java.util.List;

/** Trang thông báo, phân trang cursor; nextCursor = null khi hết. */
public record NotificationPageResponse(
        List<NotificationItemResponse> items,
        String nextCursor,
        long unreadCount
) {
}
