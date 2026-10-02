package vn.com.d2s.smr.dto.ad.employee;

public record UpdateEmployeeRequest(
        String fullName,
        String email,
        String phone,
        String address,
        String organizationId,
        String titleCode
) {
}
