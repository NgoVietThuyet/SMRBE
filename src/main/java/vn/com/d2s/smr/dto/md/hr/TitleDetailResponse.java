package vn.com.d2s.smr.dto.md.hr;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record TitleDetailResponse(
        String code,
        String name,
        String notes,
        int orderNumber,
        String permissionJson,
        @JsonProperty("isActive") boolean active,
        String createBy,
        LocalDateTime createDate,
        String updateBy,
        LocalDateTime updateDate
) {
}
