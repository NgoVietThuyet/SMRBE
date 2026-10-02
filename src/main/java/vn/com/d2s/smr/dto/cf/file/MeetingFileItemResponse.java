package vn.com.d2s.smr.dto.cf.file;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MeetingFileItemResponse(
        String id,
        String fileName,
        BigDecimal fileSize,
        String mimeType,
        int type,
        LocalDateTime createDate
) {}
