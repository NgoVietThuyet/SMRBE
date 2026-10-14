package vn.com.d2s.smr.service.nt;

import vn.com.d2s.smr.dto.nt.NotificationPageResponse;

import java.time.Instant;

/** API đọc/đánh dấu thông báo — mọi thao tác chỉ trên thông báo của chính userName. */
public interface NotificationService {

    NotificationPageResponse list(String userName, boolean unreadOnly, String cursor, int pageSize);

    long unreadCount(String userName);

    /** @throws IllegalArgumentException khi id không phải của userName (controller trả 404). */
    long markRead(String userName, String id, boolean isRead);

    long markAllRead(String userName, Instant before);

    void delete(String userName, String id);
}
