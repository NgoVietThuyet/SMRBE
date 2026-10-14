package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtLobbyRequest;

import java.util.List;
import java.util.Optional;

public interface MtLobbyRequestRepository extends JpaRepository<MtLobbyRequest, String> {

    /** Yêu cầu mới nhất của một người trong cuộc họp (quyết định trạng thái vào phòng hiện tại). */
    Optional<MtLobbyRequest> findFirstByMeetingIdAndUserNameOrderByRequestedAtDesc(String meetingId, String userName);

    List<MtLobbyRequest> findByMeetingIdAndStatusOrderByRequestedAtAsc(String meetingId, int status);
}
