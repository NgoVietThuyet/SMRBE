package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một mục checklist của việc. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtTaskChecklistItems")
public class MtTaskChecklistItem {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "TaskId", nullable = false, length = 450)
    private String taskId;

    @Column(name = "Content", nullable = false, length = 500)
    private String content = "";

    @Column(name = "IsDone", nullable = false)
    private boolean done;

    @Column(name = "SortOrder", nullable = false)
    private int sortOrder;
}
