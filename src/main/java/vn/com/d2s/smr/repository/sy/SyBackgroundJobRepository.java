package vn.com.d2s.smr.repository.sy;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.sy.SyBackgroundJob;

import java.util.Optional;

public interface SyBackgroundJobRepository extends JpaRepository<SyBackgroundJob, String> {

    Optional<SyBackgroundJob> findByIdempotencyKey(String idempotencyKey);

    Optional<SyBackgroundJob> findFirstByRefIdAndTypeOrderByCreatedAtDesc(String refId, String type);

    /** Giới hạn số lần tạo job của một người cho một cuộc họp trong khoảng thời gian gần đây. */
    long countByTypeAndRefIdAndCreatedByAndCreatedAtAfter(String type, String refId, String createdBy, java.time.LocalDateTime after);
}
