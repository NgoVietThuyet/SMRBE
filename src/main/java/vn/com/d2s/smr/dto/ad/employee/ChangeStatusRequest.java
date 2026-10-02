package vn.com.d2s.smr.dto.ad.employee;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChangeStatusRequest(
        @JsonProperty("isActive") boolean active,
        String reason
) {
}
