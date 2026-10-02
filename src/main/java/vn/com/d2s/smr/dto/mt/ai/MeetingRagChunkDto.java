package vn.com.d2s.smr.dto.mt.ai;

import java.time.LocalDateTime;

public record MeetingRagChunkDto(
        String id,
        String sourceType,
        String sourceTitle,
        String speakerName,
        String content,
        LocalDateTime timestamp,
        double relevanceScore
) {}
