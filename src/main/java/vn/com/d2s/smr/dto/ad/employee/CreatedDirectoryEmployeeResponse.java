package vn.com.d2s.smr.dto.ad.employee;

public record CreatedDirectoryEmployeeResponse(
        String userName,
        /** Mật khẩu tạm ngẫu nhiên, chỉ trả về một lần; tài khoản bị buộc đổi ở lần đăng nhập đầu. */
        String temporaryPassword
) {
}
