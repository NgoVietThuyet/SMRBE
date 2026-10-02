package vn.com.d2s.smr.dto.cf.file;

import java.math.BigDecimal;

public record UploadedFileResponse(
        String id,
        String fileName,
        BigDecimal fileSize,
        String mimeType
) {}
