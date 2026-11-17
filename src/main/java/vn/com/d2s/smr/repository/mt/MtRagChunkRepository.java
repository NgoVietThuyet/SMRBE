package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtRagChunk;

import java.util.List;

public interface MtRagChunkRepository extends JpaRepository<MtRagChunk, String> {

    List<MtRagChunk> findByMeetingIdOrderByOrd(String meetingId);

    boolean existsByMeetingId(String meetingId);

    void deleteByMeetingId(String meetingId);
}
