package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Yêu cầu vào phòng (phòng chờ) hoặc lượt bị mời ra (Status 2). Bảng 005_room.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtLobbyRequests")
public class MtLobbyRequest {

    public static final int PENDING = 0;
    public static final int ADMITTED = 1;
    public static final int REJECTED = 2;
    public static final int EXPIRED = 3;

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "DisplayName", nullable = false, length = 200)
    private String displayName = "";

    @Column(name = "Status", nullable = false)
    private int status;

    @Column(name = "RequestedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime requestedAt;

    @Column(name = "DecidedBy", length = 450)
    private String decidedBy;

    @Column(name = "DecidedAt", columnDefinition = "datetime2")
    private LocalDateTime decidedAt;
}
