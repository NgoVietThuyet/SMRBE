package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Vui lòng nhập địa chỉ Email hợp lệ.")
        @Email(message = "Vui lòng nhập địa chỉ Email hợp lệ.")
        String email
) {
}
