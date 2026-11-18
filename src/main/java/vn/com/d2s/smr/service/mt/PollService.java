package vn.com.d2s.smr.service.mt;

import vn.com.d2s.smr.dto.mt.poll.CreatePollRequest;
import vn.com.d2s.smr.dto.mt.poll.PollResponse;
import vn.com.d2s.smr.dto.mt.poll.PollSummary;

import java.util.List;

/** Biểu quyết trong cuộc họp. */
public interface PollService {

    PollResponse create(String userName, String meetingId, CreatePollRequest request);

    PollResponse vote(String userName, String meetingId, String pollId, int optionIndex);

    PollResponse close(String userName, String meetingId, String pollId);

    List<PollResponse> list(String userName, String meetingId);

    /** Người này tạo được biểu quyết lúc này (chủ trì / đồng chủ trì / thư ký, cuộc họp đang diễn ra). */
    boolean canCreate(String userName, String meetingId);

    /** Kết quả tổng (không tên người bỏ phiếu) của mọi biểu quyết trong cuộc họp, cũ trước mới sau — cho AI và hồ sơ chốt. */
    List<PollSummary> summaries(String meetingId);

    /** Đóng mọi biểu quyết còn mở (khi cuộc họp kết thúc). */
    void closeAll(String meetingId);
}
