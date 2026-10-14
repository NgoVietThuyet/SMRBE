package vn.com.d2s.smr.dto.mt.meeting;

import java.time.LocalDateTime;
import java.util.List;

/** Kiểm tra trùng lịch khi tạo/sửa cuộc họp. {@code excludeMeetingId}: bỏ qua chính cuộc họp đang sửa. */
public record ScheduleConflictRequest(
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<String> userNames,
        String excludeMeetingId
) {
}
