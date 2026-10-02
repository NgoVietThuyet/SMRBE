package vn.com.d2s.smr.dto.ad.employee;

public record ResetEmployeePasswordResponse(
        String userName,
        String temporaryPassword
) {
}
