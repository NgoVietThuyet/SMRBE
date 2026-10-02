package vn.com.d2s.smr.dto.ad.employee;

import java.time.LocalDateTime;

public record TransferEmployeeRequest(
        String organizationId,
        String reason,
        LocalDateTime effectiveDate
) {
}
