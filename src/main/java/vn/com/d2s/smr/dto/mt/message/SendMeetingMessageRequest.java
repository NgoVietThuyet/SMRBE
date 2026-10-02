package vn.com.d2s.smr.dto.mt.message;

import jakarta.validation.constraints.NotBlank;

public record SendMeetingMessageRequest(
        String receiverUserId,
        @NotBlank(message = "Nội dung tin nhắn không được để trống.")
        String messageText
) {
}
