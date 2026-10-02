package vn.com.d2s.smr.dto.ad.employee;

public record CreatedEmployeeResponse(
        String userName,
        String fullName,
        String email,
        String temporaryPassword,
        boolean mustChangePassword
) {
}
