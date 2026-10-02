package vn.com.d2s.smr.entity.ad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "AdAccounts",
        indexes = {
                @Index(name = "IX_AdAccounts_OrgId", columnList = "OrgId"),
                @Index(name = "IX_AdAccounts_TitleCode", columnList = "TitleCode")
        },
        uniqueConstraints = @UniqueConstraint(name = "IX_AdAccounts_Email", columnNames = "Email")
)
public class AdAccount extends BaseEntity {

    @Id
    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    @Column(name = "Password", nullable = false, columnDefinition = "nvarchar(max)")
    private String password = "";

    @Column(name = "FullName", nullable = false, columnDefinition = "nvarchar(max)")
    private String fullName = "";

    @Column(name = "Phone", nullable = false, columnDefinition = "nvarchar(max)")
    private String phone = "";

    @Column(name = "Email", nullable = false, length = 255)
    private String email = "";

    @Column(name = "Address", nullable = false, columnDefinition = "nvarchar(max)")
    private String address = "";

    @Column(name = "OrgId", nullable = false, length = 450)
    private String orgId = "";

    @Column(name = "TitleCode", nullable = false, length = 450)
    private String titleCode = "";

    @Column(name = "PermissionJson", columnDefinition = "nvarchar(max)")
    private String permissionJson;

    @Column(name = "RoleCodes", columnDefinition = "nvarchar(max)")
    private String roleCodes;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;

    @Column(name = "MustChangePassword", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "LastLoginAt", columnDefinition = "datetime2")
    private LocalDateTime lastLoginAt;

    @Column(name = "TokenVersion", nullable = false)
    private int tokenVersion = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "OrgId", referencedColumnName = "Id", insertable = false, updatable = false)
    private MdOrganize organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "TitleCode", referencedColumnName = "Code", insertable = false, updatable = false)
    private MdTitle title;
}

