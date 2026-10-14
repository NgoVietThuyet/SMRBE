package vn.com.d2s.smr.dto.mt.ai;

import java.time.LocalDateTime;

/**
 * Một đoạn tri thức của cuộc họp. timestamp: UTC. offset: mốc "mm:ss" tính từ lúc họp bắt đầu (chỉ đoạn transcript),
 * segmentId: đoạn transcript đầu tiên của chunk — FE dùng để hiển thị trích dẫn [Cuộc họp · ⏱ mm:ss].
 */
public record MeetingRagChunkDto(
        String id,
        String sourceType,
        String sourceTitle,
        String speakerName,
        String content,
        LocalDateTime timestamp,
        double relevanceScore,
        String offset,
        String segmentId
) {
    public MeetingRagChunkDto(String id, String sourceType, String sourceTitle, String speakerName, String content,
                              LocalDateTime timestamp, double relevanceScore) {
        this(id, sourceType, sourceTitle, speakerName, content, timestamp, relevanceScore, null, null);
    }

    public MeetingRagChunkDto withScore(double score) {
        return new MeetingRagChunkDto(id, sourceType, sourceTitle, speakerName, content, timestamp, score, offset, segmentId);
    }
}
