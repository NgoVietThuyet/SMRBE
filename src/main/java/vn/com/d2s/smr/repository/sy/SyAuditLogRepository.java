package vn.com.d2s.smr.repository.sy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.d2s.smr.entity.sy.SyAuditLog;

import java.time.LocalDateTime;

public interface SyAuditLogRepository extends JpaRepository<SyAuditLog, String> {

    @Query("""
            select l from SyAuditLog l
            where (:from is null or l.occurredAt >= :from)
              and (:to is null or l.occurredAt < :to)
              and (:category is null or l.category = :category)
              and (:actor is null or lower(l.actorUserName) like lower(concat('%', :actor, '%')))
              and (:success is null or l.success = :success)
              and (:keyword is null
                   or lower(l.action) like lower(concat('%', :keyword, '%'))
                   or lower(l.targetId) like lower(concat('%', :keyword, '%')))
            order by l.occurredAt desc
            """)
    Page<SyAuditLog> search(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("category") String category,
            @Param("actor") String actor,
            @Param("success") Boolean success,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
