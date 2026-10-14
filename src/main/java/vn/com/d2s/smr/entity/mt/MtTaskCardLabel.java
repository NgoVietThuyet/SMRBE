package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Nhãn gắn vào việc (khoá ghép TaskId + LabelId). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "MtTaskCardLabels")
@IdClass(MtTaskCardLabel.Key.class)
public class MtTaskCardLabel {

    @Id
    @Column(name = "TaskId", nullable = false, length = 450)
    private String taskId;

    @Id
    @Column(name = "LabelId", nullable = false, length = 450)
    private String labelId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String taskId;
        private String labelId;
    }
}
