package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "MeetingTasks",
        indexes = {
                @Index(name = "IX_MeetingTasks_MeetingId", columnList = "MeetingId"),
                @Index(name = "IX_MeetingTasks_AssigneeUserName", columnList = "AssigneeUserName"),
                @Index(name = "IX_MeetingTasks_DueDate", columnList = "DueDate"),
                @Index(name = "IX_MeetingTasks_ParentId", columnList = "ParentId"),
                @Index(name = "IX_MeetingTasks_Level", columnList = "Level")
        }
)
public class MeetingTask extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", length = 450)
    private String meetingId;

    @Column(name = "ParentId", length = 450)
    private String parentId;

    @Column(name = "Level", nullable = false)
    private int level;

    @Column(name = "Title", nullable = false, length = 300)
    private String title = "";

    @Column(name = "Description", length = 4000)
    private String description;

    @Column(name = "AssigneeUserName", length = 450)
    private String assigneeUserName;

    @Column(name = "DueDate", columnDefinition = "datetime2")
    private LocalDateTime dueDate;

    @Column(name = "Status", nullable = false)
    private int status;

    @Column(name = "Priority", nullable = false)
    private int priority = 1;

    @Column(name = "IsPublic", nullable = false)
    private boolean publicTask;

    @Column(name = "SourceRef", columnDefinition = "nvarchar(max)")
    private String sourceRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MeetingId", referencedColumnName = "Id", insertable = false, updatable = false)
    private MeetingInfo meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ParentId", referencedColumnName = "Id", insertable = false, updatable = false)
    private MeetingTask parent;

    @OneToMany(mappedBy = "parent")
    private List<MeetingTask> children = new ArrayList<>();
}

