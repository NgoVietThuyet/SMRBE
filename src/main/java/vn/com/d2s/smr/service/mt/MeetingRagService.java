package vn.com.d2s.smr.service.mt;

import vn.com.d2s.smr.dto.mt.ai.MeetingAiChatResponse;
import vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse;

public interface MeetingRagService {

    /**
     * Lấy trạng thái hiện tại của cơ sở tri thức (RAG) cho cuộc họp.
     */
    MeetingRagStatusResponse getRagStatus(String meetingId);

    /**
     * Thu thập toàn bộ dữ liệu cuộc họp (thông tin, người tham gia, công việc, tài liệu, transcript)
     * và lập chỉ mục cơ sở tri thức RAG.
     */
    MeetingRagStatusResponse syncKnowledgeBase(String meetingId, String currentUserName);

    /**
     * Truy vấn thông tin cuộc họp thông qua cơ chế RAG kết hợp mô hình AI LLM.
     */
    MeetingAiChatResponse askMeetingAssistant(String meetingId, String currentUserName, String query);
}
