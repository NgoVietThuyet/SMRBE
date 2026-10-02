package vn.com.d2s.smr.dto.md.hr;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record OrganizationResponse(
        String id,
        @JsonProperty("pId") String pId,
        String name,
        int orderNumber,
        boolean expanded,
        String permissionJson,
        @JsonProperty("isActive") boolean active,
        String notes,
        String createBy,
        LocalDateTime createDate,
        String updateBy,
        LocalDateTime updateDate
) {
}
