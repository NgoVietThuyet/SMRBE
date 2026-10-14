package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bình luận trên việc (CreateDate theo UTC). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtTaskComments")
public class MtTaskComment {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "TaskId", nullable = false, length = 450)
    private String taskId;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "Content", nullable = false, length = 2000)
    private String content = "";

    @Column(name = "CreateDate", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createDate;
}
