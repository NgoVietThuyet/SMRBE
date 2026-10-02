package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;

import java.util.List;
import java.util.Optional;

public interface MeetingPersonalRepository extends JpaRepository<MeetingPersonal, String> {

    List<MeetingPersonal> findByMeetingId(String meetingId);

    Optional<MeetingPersonal> findByMeetingIdAndUserName(String meetingId, String userName);

    boolean existsByMeetingIdAndUserName(String meetingId, String userName);

    int countByMeetingId(String meetingId);

    void deleteByMeetingId(String meetingId);

    void deleteByMeetingIdAndUserName(String meetingId, String userName);
}
