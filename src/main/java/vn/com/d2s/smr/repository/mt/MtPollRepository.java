package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtPoll;

import java.util.List;

public interface MtPollRepository extends JpaRepository<MtPoll, String> {

    List<MtPoll> findByMeetingIdOrderByCreatedAtAsc(String meetingId);

    List<MtPoll> findByMeetingIdAndStatus(String meetingId, int status);

    void deleteByMeetingId(String meetingId);
}
