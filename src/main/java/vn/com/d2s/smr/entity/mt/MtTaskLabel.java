package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nhãn của một bảng (công việc cấp 0). Bảng 006_task_board.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtTaskLabels")
public class MtTaskLabel {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "WorkId", nullable = false, length = 450)
    private String workId;

    @Column(name = "Name", nullable = false, length = 50)
    private String name = "";

    @Column(name = "Color", nullable = false, length = 20)
    private String color = "#61BD4F";
}
