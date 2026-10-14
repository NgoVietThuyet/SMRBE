package vn.com.d2s.smr.dto.sy;

import java.util.Map;

/** Dữ liệu một dòng nhật ký trước khi lưu. payload sẽ được lọc bỏ khóa nhạy cảm (password, token...). */
public record AuditEntry(
        String category,
        String action,
        String actorUserName,
        String targetType,
        String targetId,
        boolean success,
        int httpStatus,
        String ip,
        String userAgent,
        Map<String, Object> payload
) {
}
