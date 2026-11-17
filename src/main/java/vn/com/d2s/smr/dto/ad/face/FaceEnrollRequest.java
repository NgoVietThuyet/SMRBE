package vn.com.d2s.smr.dto.ad.face;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Đăng ký khuôn mặt: mật khẩu hiện tại + đồng ý xử lý dữ liệu sinh trắc học + vài ảnh (nhiều góc). */
public record FaceEnrollRequest(
        @NotBlank String password,
        boolean consent,
        @NotEmpty List<@NotBlank String> frames
) {
}
