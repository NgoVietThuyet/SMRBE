package vn.com.d2s.smr.dto.mt.ai;

import jakarta.validation.constraints.NotBlank;

public record MeetingAiChatRequest(
        @NotBlank(message = "Câu hỏi không được để trống.")
        String query,
        String conversationId
) {}
