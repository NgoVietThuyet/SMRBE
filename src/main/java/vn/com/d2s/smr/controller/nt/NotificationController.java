package vn.com.d2s.smr.controller.nt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.nt.MarkReadRequest;
import vn.com.d2s.smr.dto.nt.NotificationPageResponse;
import vn.com.d2s.smr.dto.nt.ReadAllRequest;
import vn.com.d2s.smr.dto.nt.UnreadCountResponse;
import vn.com.d2s.smr.service.nt.NotificationService;

import java.security.Principal;

/**
 * Trung tâm thông báo của người đang đăng nhập. Không có tham số userName:
 * mọi thao tác lấy chủ sở hữu từ JWT. Id của người khác → 404 (không lộ sự tồn tại).
 */
@RestController
@RequestMapping("/api/Notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> list(
            Principal principal,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            NotificationPageResponse page = notificationService.list(
                    principal.getName(), "unread".equalsIgnoreCase(filter), cursor, pageSize);
            return ResponseEntity.ok(ApiResponse.success(page, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(new UnreadCountResponse(notificationService.unreadCount(principal.getName())), null));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markRead(
            Principal principal,
            @PathVariable String id,
            @RequestBody(required = false) MarkReadRequest request
    ) {
        boolean isRead = request == null || request.isRead() == null || request.isRead();
        try {
            long unread = notificationService.markRead(principal.getName(), id, isRead);
            return ResponseEntity.ok(ApiResponse.success(new UnreadCountResponse(unread), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> readAll(
            Principal principal,
            @RequestBody(required = false) ReadAllRequest request
    ) {
        long unread = notificationService.markAllRead(principal.getName(), request != null ? request.before() : null);
        return ResponseEntity.ok(ApiResponse.success(new UnreadCountResponse(unread), "Đã đánh dấu tất cả là đã đọc."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Principal principal, @PathVariable String id) {
        try {
            notificationService.delete(principal.getName(), id);
            return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thông báo."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }
}
