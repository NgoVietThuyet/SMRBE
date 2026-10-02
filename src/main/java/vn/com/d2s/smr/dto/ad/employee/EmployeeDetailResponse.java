package vn.com.d2s.smr.dto.ad.employee;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record EmployeeDetailResponse(
        String userName,
        String fullName,
        String email,
        String phone,
        String address,
        String orgId,
        String organizationName,
        String titleCode,
        String titleName,
        @JsonProperty("isActive") boolean active,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        LocalDateTime createDate,
        LocalDateTime updateDate
) {
}
