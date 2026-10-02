package vn.com.d2s.smr.repository.mt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.d2s.smr.entity.mt.MeetingInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetingInfoRepository extends JpaRepository<MeetingInfo, String> {

    boolean existsByRoomCode(String roomCode);

    Optional<MeetingInfo> findByIdAndDeletedFalse(String id);

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

    @Query("""
            select m
            from MeetingInfo m
            where m.deleted = false
              and exists (
                  select 1 from MeetingPersonal p where p.meetingId = m.id and p.userName = :userName
              )
              and (:status is null or m.status = :status)
              and (:keyword is null or :keyword = '' or lower(m.name) like lower(concat('%', :keyword, '%')))
              and (:startDate is null or m.expectedStartTime >= :startDate)
              and (:endDate is null or m.expectedStartTime <= :endDate)
            order by m.expectedStartTime desc
            """)
    Page<MeetingInfo> searchMeetings(
            @Param("userName") String userName,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("""
            select m
            from MeetingInfo m
            where m.deleted = false
              and m.status = 1
              and exists (
                  select 1 from MeetingPersonal p where p.meetingId = m.id and p.userName = :userName
              )
            order by m.expectedStartTime asc
            """)
    List<MeetingInfo> findNextMeetings(@Param("userName") String userName, Pageable pageable);
}
