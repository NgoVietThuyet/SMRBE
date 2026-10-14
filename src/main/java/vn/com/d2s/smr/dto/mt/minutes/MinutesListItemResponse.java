package vn.com.d2s.smr.dto.mt.minutes;

import java.time.LocalDateTime;

/** Trang danh sách biên bản / Trợ lý AI: cuộc họp + số đoạn transcript + trạng thái biên bản mới nhất. */
public record MinutesListItemResponse(
        String meetingId,
        String meetingName,
        String roomCode,
        int meetingStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String hostName,
        int transcriptCount,
        Integer minutesVersion,
        Integer minutesStatus,
        LocalDateTime publishedAt
) {
}
