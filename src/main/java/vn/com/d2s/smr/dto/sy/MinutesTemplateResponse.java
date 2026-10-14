package vn.com.d2s.smr.dto.sy;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mẫu biên bản DOCX (CmFiles Type = 5). */
public record MinutesTemplateResponse(
        String id,
        String fileName,
        BigDecimal fileSize,
        @JsonProperty("isDefault") boolean defaultTemplate,
        String createBy,
        LocalDateTime createDate
) {
}
