package vn.com.d2s.smr.dto.mt.ai;

import java.time.LocalDateTime;
import java.util.List;

public record MeetingAiChatResponse(
        String answer,
        List<MeetingRagChunkDto> citations,
        LocalDateTime timestamp,
        String model,
        boolean success
) {
    public static MeetingAiChatResponse success(String answer, List<MeetingRagChunkDto> citations, String model) {
        return new MeetingAiChatResponse(answer, citations, LocalDateTime.now(), model, true);
    }

    public static MeetingAiChatResponse fallback(String answer, List<MeetingRagChunkDto> citations) {
        return new MeetingAiChatResponse(answer, citations, LocalDateTime.now(), "fallback", false);
    }
}
