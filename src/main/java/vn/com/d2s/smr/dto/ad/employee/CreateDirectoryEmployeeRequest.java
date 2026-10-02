package vn.com.d2s.smr.dto.ad.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDirectoryEmployeeRequest(
        @NotBlank(message = "Họ tên không được trống.")
        @Size(max = 200, message = "Họ tên tối đa 200 ký tự.")
        String fullName,

        @NotBlank(message = "Email không được trống.")
        @Email(message = "Email không hợp lệ.")
        String email,

        String phone,
        String address,

        @NotBlank(message = "Phòng ban không được trống.")
        String organizationId,

        @NotBlank(message = "Chức danh không được trống.")
        String titleCode
) {
}
