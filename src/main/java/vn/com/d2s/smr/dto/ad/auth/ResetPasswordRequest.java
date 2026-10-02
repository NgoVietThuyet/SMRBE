package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "Vui lòng nhập đầy đủ thông tin ResetToken và mật khẩu mới.")
        String resetToken,
        @NotBlank(message = "Vui lòng nhập đầy đủ thông tin ResetToken và mật khẩu mới.")
        String newPassword
) {
}
