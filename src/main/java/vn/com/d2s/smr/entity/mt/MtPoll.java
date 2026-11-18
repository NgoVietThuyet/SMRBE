package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Biểu quyết trong cuộc họp. OptionsJson = mảng chuỗi phương án. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtPolls")
public class MtPoll {

    public static final int OPEN = 0;
    public static final int CLOSED = 1;

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "Question", nullable = false, length = 500)
    private String question;

    @Column(name = "OptionsJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String optionsJson = "[]";

    @Column(name = "Anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "Status", nullable = false)
    private int status;

    @Column(name = "CreatedBy", nullable = false, length = 450)
    private String createdBy;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "ClosedAt", columnDefinition = "datetime2")
    private LocalDateTime closedAt;
}
