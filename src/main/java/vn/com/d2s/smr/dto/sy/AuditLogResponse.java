package vn.com.d2s.smr.dto.sy;

import java.time.LocalDateTime;

public record AuditLogResponse(
        String id,
        String category,
        String action,
        String actorUserName,
        String targetType,
        String targetId,
        boolean success,
        int httpStatus,
        String ip,
        String userAgent,
        String payloadJson,
        LocalDateTime occurredAt
) {
}
