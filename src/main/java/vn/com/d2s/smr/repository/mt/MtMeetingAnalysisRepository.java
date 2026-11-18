package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtMeetingAnalysis;

import java.util.Optional;

public interface MtMeetingAnalysisRepository extends JpaRepository<MtMeetingAnalysis, String> {

    Optional<MtMeetingAnalysis> findByMeetingId(String meetingId);

    void deleteByMeetingId(String meetingId);
}
