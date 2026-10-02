package vn.com.d2s.smr.dto.md.hr;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record OrganizationRequest(
        @NotBlank(message = "Tên phòng ban không được trống.")
        String name,
        String parentId,
        int orderNumber,
        @JsonProperty("isActive")
        boolean active,
        String notes
) {
}
