package vn.com.d2s.smr.dto.mt.meeting;

import java.time.LocalDateTime;

public record MeetingAuditResponse(
        String id,
        String action,
        String actorId,
        LocalDateTime occurredAt,
        int version,
        String payloadJson
) {
}
