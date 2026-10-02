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

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "MeetingPersonals",
        uniqueConstraints = @UniqueConstraint(
                name = "IX_MeetingPersonals_MeetingId_UserName",
                columnNames = {"MeetingId", "UserName"}
        )
)
public class MeetingPersonal extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "FullName", nullable = false, columnDefinition = "nvarchar(max)")
    private String fullName = "";

    @Column(name = "Phone", nullable = false, columnDefinition = "nvarchar(max)")
    private String phone = "";

    @Column(name = "Email", nullable = false, columnDefinition = "nvarchar(max)")
    private String email = "";

    @Column(name = "Address", nullable = false, columnDefinition = "nvarchar(max)")
    private String address = "";

    @Column(name = "OrgId", nullable = false, columnDefinition = "nvarchar(max)")
    private String orgId = "";

    @Column(name = "TitleCode", nullable = false, columnDefinition = "nvarchar(max)")
    private String titleCode = "";

    @Column(name = "RefrenceFileId", nullable = false, columnDefinition = "nvarchar(max)")
    private String referenceFileId = "";

    @Column(name = "Type", nullable = false)
    private int type;

    @Column(name = "IsChuTri", nullable = false)
    private boolean chairperson;

    @Column(name = "IsJoined", nullable = false)
    private boolean joined;

    @Column(name = "JoinTime", columnDefinition = "datetime2")
    private LocalDateTime joinTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MeetingId", referencedColumnName = "Id", insertable = false, updatable = false)
    private MeetingInfo meeting;
}

