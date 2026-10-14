package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtWhiteboard;

import java.util.Optional;

public interface MtWhiteboardRepository extends JpaRepository<MtWhiteboard, String> {

    Optional<MtWhiteboard> findFirstByMeetingId(String meetingId);
}
