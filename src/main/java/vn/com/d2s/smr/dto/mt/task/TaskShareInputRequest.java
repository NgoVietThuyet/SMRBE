package vn.com.d2s.smr.dto.mt.task;

import jakarta.validation.constraints.NotBlank;

public record TaskShareInputRequest(
        @NotBlank(message = "Tên người được chia sẻ không được để trống.")
        String userName,
        int permission
) {
}
