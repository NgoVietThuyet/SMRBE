package vn.com.d2s.smr.dto.mt.task.board;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaskFileResponse(String id, String fileName, BigDecimal fileSize, String mimeType, String createBy,
                               LocalDateTime createDate, boolean canDelete) {
}
