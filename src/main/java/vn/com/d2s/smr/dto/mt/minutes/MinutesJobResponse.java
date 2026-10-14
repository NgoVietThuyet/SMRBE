package vn.com.d2s.smr.dto.mt.minutes;

import java.time.LocalDateTime;

/** status: 0 chờ · 1 đang chạy · 2 xong · 3 lỗi. */
public record MinutesJobResponse(String id, int status, String error, LocalDateTime createdAt, LocalDateTime finishedAt,
                                 boolean reused) {
}
