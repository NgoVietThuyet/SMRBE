package vn.com.d2s.smr.dto.ad.employee;

import java.time.LocalDateTime;
import java.util.List;

/** Cuộc họp chưa kết thúc mà tài khoản là chủ trì, kèm người có thể nhận chuyển chủ trì. */
public record OwnedMeetingResponse(
        String meetingId,
        String name,
        int status,
        LocalDateTime expectedStartTime,
        List<Candidate> candidates
) {
    public record Candidate(String userName, String fullName) {
    }
}
