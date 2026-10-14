package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtAttendanceEvent;

import java.util.List;

public interface MtAttendanceEventRepository extends JpaRepository<MtAttendanceEvent, String> {

    List<MtAttendanceEvent> findByMeetingIdOrderByOccurredAtAsc(String meetingId);
}
