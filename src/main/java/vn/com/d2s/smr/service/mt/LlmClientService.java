package vn.com.d2s.smr.service.mt;

import java.util.List;

public interface LlmClientService {

    /**
     * Gửi yêu cầu hoàn thành hội thoại tới mô hình LLM.
     *
     * @param systemPrompt Lời nhắc hệ thống định hình vai trò và ngữ cảnh RAG
     * @param userPrompt Câu hỏi hoặc yêu cầu của người dùng
     * @return Câu trả lời từ LLM
     */
    String generateAnswer(String systemPrompt, String userPrompt);

    /**
     * Kiểm tra trạng thái sẵn sàng kết nối của mô hình LLM.
     */
    boolean isAvailable();
}
