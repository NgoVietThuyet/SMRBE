package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Bảng trắng của cuộc họp (một bảng / cuộc họp). ContentJson = mảng nét vẽ; Revision tăng mỗi lần lưu. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtWhiteboards")
public class MtWhiteboard {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "Name", nullable = false, length = 200)
    private String name = "Bảng trắng";

    @Column(name = "Revision", nullable = false)
    private int revision;

    @Column(name = "ContentJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String contentJson = "[]";

    @Column(name = "IsLocked", nullable = false)
    private boolean locked;

    @Column(name = "UpdatedBy", length = 450)
    private String updatedBy;

    @Column(name = "UpdatedAt", columnDefinition = "datetime2")
    private LocalDateTime updatedAt;
}
