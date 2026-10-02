package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập đầy đủ mật khẩu hiện tại và mật khẩu mới.")
        String currentPassword,
        @NotBlank(message = "Vui lòng nhập đầy đủ mật khẩu hiện tại và mật khẩu mới.")
        String newPassword
) {
}
