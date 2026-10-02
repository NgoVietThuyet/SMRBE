package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "MeetingAuditLogs",
        indexes = @Index(
                name = "IX_MeetingAuditLogs_MeetingId_OccurredAt",
                columnList = "MeetingId,OccurredAt"
        )
)
public class MeetingAuditLog {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "Action", nullable = false, columnDefinition = "nvarchar(max)")
    private String action = "";

    @Column(name = "ActorId", nullable = false, columnDefinition = "nvarchar(max)")
    private String actorId = "";

    @Column(name = "OccurredAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime occurredAt;

    @Column(name = "CorrelationId", nullable = false, columnDefinition = "nvarchar(max)")
    private String correlationId = "";

    @Column(name = "Version", nullable = false)
    private int version;

    @Column(name = "PayloadJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String payloadJson = "";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MeetingId", referencedColumnName = "Id", insertable = false, updatable = false)
    private MeetingInfo meeting;
}

