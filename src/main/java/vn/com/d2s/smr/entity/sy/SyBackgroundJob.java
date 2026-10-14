package vn.com.d2s.smr.entity.sy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Job nền có IdempotencyKey (P13: sinh biên bản) — 007_minutes.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SyBackgroundJobs")
public class SyBackgroundJob {

    public static final int PENDING = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "Type", nullable = false, length = 50)
    private String type;

    @Column(name = "IdempotencyKey", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "RefId", length = 450)
    private String refId;

    @Column(name = "Status", nullable = false)
    private int status;

    @Column(name = "ResultRef", length = 450)
    private String resultRef;

    @Column(name = "Error", length = 2000)
    private String error;

    @Column(name = "CreatedBy", nullable = false, length = 450)
    private String createdBy;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "StartedAt", columnDefinition = "datetime2")
    private LocalDateTime startedAt;

    @Column(name = "FinishedAt", columnDefinition = "datetime2")
    private LocalDateTime finishedAt;
}
