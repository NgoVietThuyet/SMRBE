package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.com.d2s.smr.entity.BaseEntity;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "MeetingInfos",
        indexes = @Index(
                name = "IX_MeetingInfos_Status_ExpectedStartTime",
                columnList = "Status,ExpectedStartTime"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "IX_MeetingInfos_RoomCode",
                columnNames = "RoomCode"
        )
)
public class MeetingInfo extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "Name", nullable = false, columnDefinition = "nvarchar(max)")
    private String name = "";

    @Column(name = "ExpectedStartTime", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime expectedStartTime;

    @Column(name = "StartDate", columnDefinition = "datetime2")
    private LocalDateTime startDate;

    @Column(name = "EndDate", columnDefinition = "datetime2")
    private LocalDateTime endDate;

    @Column(name = "MeetContent", nullable = false, columnDefinition = "nvarchar(max)")
    private String meetContent = "";

    @Column(name = "Status", nullable = false)
    private int status;

    @Column(name = "Notes", nullable = false, columnDefinition = "nvarchar(max)")
    private String notes = "";

    @Column(name = "RefrenceFileId", nullable = false, columnDefinition = "nvarchar(max)")
    private String referenceFileId = "";

    @Column(name = "ExpectedEndTime", columnDefinition = "datetime2")
    private LocalDateTime expectedEndTime;

    @Column(name = "TimeZone", nullable = false, columnDefinition = "nvarchar(max)")
    private String timeZone = "Asia/Bangkok";

    @Column(name = "Agenda", nullable = false, columnDefinition = "nvarchar(max)")
    private String agenda = "";

    @Column(name = "Visibility", nullable = false)
    private int visibility;

    @Column(name = "RoomCode", nullable = false, length = 450)
    private String roomCode = "";

    @Column(name = "JoinUrl", nullable = false, columnDefinition = "nvarchar(max)")
    private String joinUrl = "";

    @Column(name = "SettingsJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String settingsJson = "{\"schemaVersion\":1}";

    @Column(name = "CancellationReason", nullable = false, columnDefinition = "nvarchar(max)")
    private String cancellationReason = "";

    @Column(name = "IsDraft", nullable = false)
    private boolean draft;

    @Column(name = "IsArchived", nullable = false)
    private boolean archived;

    @Column(name = "IsDeleted", nullable = false)
    private boolean deleted;

    @Column(name = "Version", nullable = false)
    private int version = 1;

    @Column(
            name = "RowVersion",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "rowversion"
    )
    @JdbcTypeCode(SqlTypes.BINARY)
    private byte[] rowVersion;
}
