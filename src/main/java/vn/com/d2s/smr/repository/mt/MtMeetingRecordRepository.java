package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtMeetingRecord;

import java.util.Optional;

public interface MtMeetingRecordRepository extends JpaRepository<MtMeetingRecord, String> {

    Optional<MtMeetingRecord> findByMeetingId(String meetingId);

    void deleteByMeetingId(String meetingId);
}
