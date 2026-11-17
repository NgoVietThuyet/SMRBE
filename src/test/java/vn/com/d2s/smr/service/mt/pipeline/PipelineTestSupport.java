package vn.com.d2s.smr.service.mt.pipeline;

import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.service.mt.LlmClientService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Dùng chung cho test pipeline: LLM giả ghi lại các lần gọi, trả lời theo hàm do test cung cấp. */
final class PipelineTestSupport {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 15, 3, 0);

    private PipelineTestSupport() {
    }

    static final class FakeLlm implements LlmClientService {
        final List<String> systems = new ArrayList<>();
        final List<String> users = new ArrayList<>();
        private final BiFunction<String, String, String> answer;

        FakeLlm(BiFunction<String, String, String> answer) {
            this.answer = answer;
        }

        @Override
        public String generateAnswer(String systemPrompt, String userPrompt) {
            return generate(systemPrompt, userPrompt, 0.2, 1000);
        }

        @Override
        public String generate(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
            systems.add(systemPrompt);
            users.add(userPrompt);
            return answer.apply(systemPrompt, userPrompt);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    static MeetingTranscriptSegment seg(String id, String userName, String speaker, String text, int minute) {
        MeetingTranscriptSegment s = new MeetingTranscriptSegment();
        s.setId(id);
        s.setMeetingId("m1");
        s.setSpeakerUserName(userName);
        s.setSpeakerName(speaker);
        s.setText(text);
        s.setSpokenAt(T0.plusMinutes(minute));
        return s;
    }
}
