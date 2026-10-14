package vn.com.d2s.smr.repository.nt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.d2s.smr.entity.nt.Notification;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdAndRecipientUserName(String id, String recipientUserName);

    Optional<Notification> findByRecipientUserNameAndDedupeKey(String recipientUserName, String dedupeKey);

    long countByRecipientUserNameAndReadFalse(String recipientUserName);

    @Modifying
    @Query("update Notification n set n.read = true, n.readAt = :now "
            + "where n.recipientUserName = :userName and n.read = false and n.createDate <= :before")
    int markAllRead(@Param("userName") String userName, @Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

    /** Dọn: đã đọc quá hạn lưu, hoặc mọi thông báo quá hạn tối đa. */
    @Modifying
    @Query("delete from Notification n where (n.read = true and n.createDate < :readBefore) or n.createDate < :allBefore")
    int deleteExpired(@Param("readBefore") LocalDateTime readBefore, @Param("allBefore") LocalDateTime allBefore);
}
