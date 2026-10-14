package vn.com.d2s.smr.entity.sy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Nhật ký hành động quan trọng toàn hệ thống (SMR-DB §6, phase P6). Chỉ ghi thêm, không sửa/xóa. */
@Getter
@Setter
@Entity
@Table(
        name = "SyAuditLogs",
        indexes = {
                @Index(name = "IX_SyAuditLogs_OccurredAt", columnList = "OccurredAt"),
                @Index(name = "IX_SyAuditLogs_ActorUserName", columnList = "ActorUserName"),
                @Index(name = "IX_SyAuditLogs_Category", columnList = "Category")
        }
)
public class SyAuditLog {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "Category", nullable = false, length = 30)
    private String category;

    @Column(name = "Action", nullable = false, length = 100)
    private String action;

    @Column(name = "ActorUserName", length = 450)
    private String actorUserName;

    @Column(name = "TargetType", length = 50)
    private String targetType;

    @Column(name = "TargetId", length = 450)
    private String targetId;

    @Column(name = "Success", nullable = false)
    private boolean success;

    @Column(name = "HttpStatus", nullable = false)
    private int httpStatus;

    @Column(name = "Ip", length = 64)
    private String ip;

    @Column(name = "UserAgent", length = 500)
    private String userAgent;

    @Column(name = "PayloadJson", columnDefinition = "nvarchar(max)")
    private String payloadJson;

    @Column(name = "OccurredAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime occurredAt;
}
