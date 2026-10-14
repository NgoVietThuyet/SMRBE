package vn.com.d2s.smr.dto.mt.minutes;

import java.time.LocalDateTime;
import java.util.List;

/** Một phiên bản biên bản. warnings: mục thiếu nguồn (không cho phát hành). */
public record MinutesVersionResponse(
        String id,
        int version,
        int status,
        MinutesContent content,
        String source,
        String model,
        String promptVersion,
        String rejectReason,
        String createdBy,
        LocalDateTime createdAt,
        String updatedBy,
        LocalDateTime updatedAt,
        String submittedBy,
        LocalDateTime submittedAt,
        String approvedBy,
        LocalDateTime approvedAt,
        String publishedBy,
        LocalDateTime publishedAt,
        List<String> warnings
) {
}
