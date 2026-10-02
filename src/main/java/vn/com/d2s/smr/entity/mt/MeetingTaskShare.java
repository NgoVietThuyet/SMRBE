package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "MeetingTaskShares",
        uniqueConstraints = @UniqueConstraint(
                name = "IX_MeetingTaskShares_TaskId_UserName",
                columnNames = {"TaskId", "UserName"}
        )
)
public class MeetingTaskShare extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "TaskId", nullable = false, length = 450)
    private String taskId;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "Permission", nullable = false)
    private int permission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TaskId", referencedColumnName = "Id", insertable = false, updatable = false)
    private MeetingTask task;
}

