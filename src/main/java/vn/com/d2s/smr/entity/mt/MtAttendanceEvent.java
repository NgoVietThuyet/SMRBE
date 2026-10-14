package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một lần vào (0) hoặc rời (1) phòng họp của một kết nối (ClientSessionId). Bảng 005_room.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtAttendanceEvents")
public class MtAttendanceEvent {

    public static final int ENTER = 0;
    public static final int LEAVE = 1;

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "EventType", nullable = false)
    private int eventType;

    @Column(name = "OccurredAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime occurredAt;

    @Column(name = "ClientSessionId", nullable = false, length = 100)
    private String clientSessionId;
}
