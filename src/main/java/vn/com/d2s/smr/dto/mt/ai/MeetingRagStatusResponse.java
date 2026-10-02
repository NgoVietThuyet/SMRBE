package vn.com.d2s.smr.dto.mt.ai;

import java.time.LocalDateTime;

public record MeetingRagStatusResponse(
        String meetingId,
        boolean indexed,
        int chunkCount,
        LocalDateTime lastIndexedAt,
        String sourcesSummary
) {}
