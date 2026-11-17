package vn.com.d2s.smr.dto.ad.face;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Đăng nhập 1:1: tên đăng nhập + mã thử thách + vài khung hình JPEG (base64). */
public record FaceLoginRequest(
        @NotBlank @Size(max = 450) String userName,
        @NotBlank @Size(max = 100) String challengeId,
        @NotEmpty List<@NotBlank String> frames
) {
}
