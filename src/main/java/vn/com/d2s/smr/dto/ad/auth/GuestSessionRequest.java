package vn.com.d2s.smr.dto.ad.auth;

import jakarta.validation.constraints.NotBlank;

public record GuestSessionRequest(
        @NotBlank(message = "Mã cuộc họp không được để trống.")
        String meetingId,

        @NotBlank(message = "Tên hiển thị không được để trống.")
        String displayName
) {
}
