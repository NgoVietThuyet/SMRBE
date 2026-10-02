package vn.com.d2s.smr.dto.md.hr;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record TitleRequest(
        @NotBlank(message = "Mã chức danh không được trống.")
        String code,
        @NotBlank(message = "Tên chức danh không được trống.")
        String name,
        String notes,
        int orderNumber,
        @JsonProperty("isActive")
        boolean active
) {
}
