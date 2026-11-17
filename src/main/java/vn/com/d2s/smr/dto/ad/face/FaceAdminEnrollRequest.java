package vn.com.d2s.smr.dto.ad.face;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Quản trị viên đăng ký khuôn mặt cho tài khoản vừa tạo (người dùng đứng trước camera): consent = người dùng đồng ý. */
public record FaceAdminEnrollRequest(
        boolean consent,
        @NotEmpty List<@jakarta.validation.constraints.NotBlank String> frames
) {
}
