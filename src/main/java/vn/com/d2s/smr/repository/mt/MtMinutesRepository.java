package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtMinutes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MtMinutesRepository extends JpaRepository<MtMinutes, String> {

    List<MtMinutes> findByMeetingIdOrderByVersionDesc(String meetingId);

    Optional<MtMinutes> findByMeetingIdAndVersion(String meetingId, int version);

    Optional<MtMinutes> findFirstByMeetingIdOrderByVersionDesc(String meetingId);

    Optional<MtMinutes> findFirstByMeetingIdAndStatusOrderByVersionDesc(String meetingId, int status);

    List<MtMinutes> findByMeetingIdIn(Collection<String> meetingIds);
}
