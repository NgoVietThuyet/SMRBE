package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một phiên bản biên bản — 007_minutes.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtMinutes")
public class MtMinutes {

    public static final int DRAFT = 0;
    public static final int SUBMITTED = 1;
    public static final int APPROVED = 2;
    public static final int PUBLISHED = 3;
    public static final int REJECTED = 4;

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "Version", nullable = false)
    private int version;

    @Column(name = "Status", nullable = false)
    private int status;

    @Column(name = "ContentJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String contentJson = "{}";

    @Column(name = "Source", nullable = false, length = 20)
    private String source = "MANUAL";

    @Column(name = "Model", length = 100)
    private String model;

    @Column(name = "PromptVersion", length = 50)
    private String promptVersion;

    @Column(name = "TranscriptHash", length = 64)
    private String transcriptHash;

    @Column(name = "RejectReason", length = 1000)
    private String rejectReason;

    @Column(name = "CreatedBy", nullable = false, length = 450)
    private String createdBy;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedBy", length = 450)
    private String updatedBy;

    @Column(name = "UpdatedAt", columnDefinition = "datetime2")
    private LocalDateTime updatedAt;

    @Column(name = "SubmittedBy", length = 450)
    private String submittedBy;

    @Column(name = "SubmittedAt", columnDefinition = "datetime2")
    private LocalDateTime submittedAt;

    @Column(name = "ApprovedBy", length = 450)
    private String approvedBy;

    @Column(name = "ApprovedAt", columnDefinition = "datetime2")
    private LocalDateTime approvedAt;

    @Column(name = "PublishedBy", length = 450)
    private String publishedBy;

    @Column(name = "PublishedAt", columnDefinition = "datetime2")
    private LocalDateTime publishedAt;
}
