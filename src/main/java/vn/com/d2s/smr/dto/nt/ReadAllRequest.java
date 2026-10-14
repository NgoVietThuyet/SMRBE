package vn.com.d2s.smr.dto.nt;

import java.time.Instant;

/** before: chỉ đánh dấu thông báo tạo trước mốc này (tránh "nuốt" thông báo vừa đến). */
public record ReadAllRequest(Instant before) {
}
