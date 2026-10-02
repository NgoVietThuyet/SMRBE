package vn.com.d2s.smr.dto.md.hr;

import java.util.List;

public record HumanResourceSummaryResponse(
        int organizationCount,
        int employeeCount,
        int activeAccountCount,
        int lockedAccountCount,
        List<OrgEmployeeCountResponse> employeesByOrganization
) {
}
