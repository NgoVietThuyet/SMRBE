package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Vui lòng cung cấp Refresh Token hợp lệ.")
        String refreshToken
) {
}
