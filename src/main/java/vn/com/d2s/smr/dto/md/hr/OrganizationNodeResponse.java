package vn.com.d2s.smr.dto.md.hr;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizationNodeResponse(
        String id,
        @JsonProperty("pId") String pId,
        String name,
        int orderNumber,
        boolean expanded,
        @JsonProperty("isActive") boolean active,
        String notes,
        long employeeCount
) {
}
