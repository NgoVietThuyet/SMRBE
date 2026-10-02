package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập đầy đủ thông tin Tên đăng nhập và Mật khẩu.")
        String userName,
        @NotBlank(message = "Vui lòng nhập đầy đủ thông tin Tên đăng nhập và Mật khẩu.")
        String password
) {
}

