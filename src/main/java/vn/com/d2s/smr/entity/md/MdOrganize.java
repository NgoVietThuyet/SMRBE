package vn.com.d2s.smr.entity.md;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MdOrganizes", indexes = @Index(name = "IX_MdOrganizes_PId", columnList = "PId"))
public class MdOrganize extends BaseEntity {

    @Id
    @Column(name = "Id", nullable = false, length = 450)
    private String id;

    @Column(name = "PId", nullable = false, length = 450)
    private String parentId = "";

    @Column(name = "Name", nullable = false, columnDefinition = "nvarchar(max)")
    private String name = "";

    @Column(name = "OrderNumber", nullable = false)
    private int orderNumber;

    @Column(name = "Expanded", nullable = false)
    private boolean expanded;

    @Column(name = "PermissionJson", columnDefinition = "nvarchar(max)")
    private String permissionJson;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;

    @Column(name = "Notes", columnDefinition = "nvarchar(max)")
    private String notes;
}

