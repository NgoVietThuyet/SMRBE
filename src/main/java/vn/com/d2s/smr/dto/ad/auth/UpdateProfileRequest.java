package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống.")
        @Size(max = 200, message = "Họ và tên không được vượt quá 200 ký tự.")
        String fullName,
        @NotBlank(message = "Email không được để trống.")
        @Email(message = "Email không hợp lệ.")
        @Size(max = 200, message = "Email không được vượt quá 200 ký tự.")
        String email,
        @Size(max = 50, message = "Số điện thoại không được vượt quá 50 ký tự.")
        String phone,
        @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự.")
        String address
) {
}
