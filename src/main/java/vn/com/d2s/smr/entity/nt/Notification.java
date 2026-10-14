package vn.com.d2s.smr.entity.nt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thông báo của đúng MỘT người nhận (RecipientUserName là chủ sở hữu duy nhất).
 * Bảng tạo bằng script BE-Java/db/2026_10_notifications.sql.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Notifications")
public class Notification {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "RecipientUserName", nullable = false, length = 450)
    private String recipientUserName;

    @Column(name = "Type", nullable = false, length = 50)
    private String type;

    /** 0 Thông tin · 1 Cảnh báo · 2 Lỗi · 3 Cần xác nhận. */
    @Column(name = "Severity", nullable = false, columnDefinition = "tinyint")
    private int severity;

    @Column(name = "Title", nullable = false, length = 300)
    private String title;

    @Column(name = "Body", length = 1000)
    private String body;

    /** meeting · task · none. */
    @Column(name = "ResourceType", nullable = false, length = 30)
    private String resourceType;

    @Column(name = "ResourceId", length = 450)
    private String resourceId;

    @Column(name = "MeetingId", length = 450)
    private String meetingId;

    @Column(name = "ActorUserName", length = 450)
    private String actorUserName;

    @Column(name = "ActorFullName", length = 300)
    private String actorFullName;

    @Column(name = "DataJson", columnDefinition = "nvarchar(max)")
    private String dataJson;

    @Column(name = "DedupeKey", nullable = false, length = 300)
    private String dedupeKey;

    @Column(name = "IsRead", nullable = false)
    private boolean read;

    @Column(name = "ReadAt")
    private LocalDateTime readAt;

    /** UTC. */
    @Column(name = "CreateDate", nullable = false)
    private LocalDateTime createDate;
}
