package vn.com.d2s.smr.repository.mt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.d2s.smr.entity.mt.MeetingInfo;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MeetingInfoRepository extends JpaRepository<MeetingInfo, String> {

    boolean existsByRoomCode(String roomCode);

    Optional<MeetingInfo> findByIdAndDeletedFalse(String id);

    Optional<MeetingInfo> findFirstByRoomCodeIgnoreCaseAndDeletedFalse(String roomCode);

    Optional<MeetingInfo> findFirstByReferenceFileId(String referenceFileId);

    @Query("""
            select count(m)
            from MeetingInfo m
            where m.deleted = false
              and m.status = :status
              and exists (
                  select 1 from MeetingPersonal p where p.meetingId = m.id and p.userName = :userName
              )
            """)
    int countByStatusAndUser(@Param("status") int status, @Param("userName") String userName);

    /**
     * Danh sách "Cuộc họp của tôi". {@code anyStatus = true} → không lọc trạng thái ({@code statuses} vẫn phải
     * khác rỗng vì IN rỗng không hợp lệ). Thứ tự sắp xếp lấy từ {@code pageable}.
     */
    @Query("""
            select m
            from MeetingInfo m
            where m.deleted = false
              and exists (
                  select 1 from MeetingPersonal p where p.meetingId = m.id and p.userName = :userName
              )
              and (:anyStatus = true or m.status in :statuses)
              and (:keyword is null or :keyword = ''
                   or lower(m.name) like lower(concat('%', :keyword, '%'))
                   or lower(m.roomCode) like lower(concat('%', :keyword, '%')))
              and (:startDate is null or m.expectedStartTime >= :startDate)
              and (:endDate is null or m.expectedStartTime <= :endDate)
            """)
    Page<MeetingInfo> searchMeetings(
            @Param("userName") String userName,
            @Param("anyStatus") boolean anyStatus,
            @Param("statuses") Collection<Integer> statuses,
            @Param("keyword") String keyword,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Cuộc họp đã lên lịch / đang diễn ra của các {@code userNames} (viết thường) giao với khoảng [startTime, endTime).
     * Mỗi dòng là cặp (MeetingInfo, MeetingPersonal) để biết ai bị trùng.
     */
    @Query("""
            select m, p
            from MeetingInfo m, MeetingPersonal p
            where p.meetingId = m.id
              and m.deleted = false
              and m.status in (1, 2)
              and (:excludeId is null or m.id <> :excludeId)
              and lower(p.userName) in :userNames
              and m.expectedStartTime < :endTime
              and m.expectedEndTime > :startTime
            order by m.expectedStartTime asc
            """)
    List<Object[]> findScheduleConflicts(
            @Param("userNames") Collection<String> userNames,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") String excludeId
    );

    @Query("""
            select m
            from MeetingInfo m
            where m.deleted = false
              and m.status = 1
              and m.expectedStartTime >= :fromTime
              and exists (
                  select 1 from MeetingPersonal p where p.meetingId = m.id and p.userName = :userName
              )
            order by m.expectedStartTime asc
            """)
    List<MeetingInfo> findNextMeetings(
            @Param("userName") String userName,
            @Param("fromTime") LocalDateTime fromTime,
            Pageable pageable
    );
}
