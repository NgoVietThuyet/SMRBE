package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Hồ sơ cuộc họp chốt khi họp kết thúc — 008_meeting_record.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtMeetingRecords")
public class MtMeetingRecord {

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "Revision", nullable = false)
    private int revision;

    @Column(name = "SourceHash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "SnapshotJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String snapshotJson = "{}";

    @Column(name = "SealedBy", nullable = false, length = 450)
    private String sealedBy;

    @Column(name = "SealedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime sealedAt;
}
