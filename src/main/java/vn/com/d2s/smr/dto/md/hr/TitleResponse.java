package vn.com.d2s.smr.dto.md.hr;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TitleResponse(
        String code,
        String name,
        String notes,
        int orderNumber,
        @JsonProperty("isActive") boolean active,
        long employeeCount
) {
}
