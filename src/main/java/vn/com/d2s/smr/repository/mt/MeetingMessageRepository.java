package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MeetingMessage;

import java.util.List;

public interface MeetingMessageRepository extends JpaRepository<MeetingMessage, String> {

    List<MeetingMessage> findByMeetingIdOrderByCreateDateAsc(String meetingId);
}
