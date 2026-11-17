package vn.com.d2s.smr.dto.ad.face;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Vào họp bằng mặt qua link: mã phòng + mã thử thách + khung hình. Chỉ nhận dạng trong số thành viên cuộc họp. */
public record FaceJoinRequest(
        @NotBlank @Size(max = 100) String roomCode,
        @NotBlank @Size(max = 100) String challengeId,
        @NotEmpty List<@NotBlank String> frames
) {
}
