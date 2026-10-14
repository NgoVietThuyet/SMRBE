package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;

import java.util.Collection;
import java.util.List;

public interface MeetingTranscriptSegmentRepository extends JpaRepository<MeetingTranscriptSegment, String> {

    List<MeetingTranscriptSegment> findByMeetingIdOrderBySpokenAtAsc(String meetingId);

    void deleteByMeetingId(String meetingId);

    /** Số đoạn transcript của nhiều cuộc họp (trang danh sách biên bản). Mỗi dòng: [meetingId, count]. */
    @Query("select s.meetingId, count(s) from MeetingTranscriptSegment s where s.meetingId in :ids group by s.meetingId")
    List<Object[]> countByMeetingIds(@Param("ids") Collection<String> ids);
}
