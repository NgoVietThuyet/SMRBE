package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MeetingAuditLog;

import java.util.List;

public interface MeetingAuditLogRepository extends JpaRepository<MeetingAuditLog, String> {

    List<MeetingAuditLog> findByMeetingIdOrderByOccurredAtDesc(String meetingId);
}
