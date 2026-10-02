package vn.com.d2s.smr.entity.md;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.com.d2s.smr.entity.BaseEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MdTitles")
public class MdTitle extends BaseEntity {

    @Id
    @Column(name = "Code", nullable = false, length = 450)
    private String code;

    @Column(name = "Name", nullable = false, columnDefinition = "nvarchar(max)")
    private String name = "";

    @Column(name = "Notes", nullable = false, columnDefinition = "nvarchar(max)")
    private String notes = "";

    @Column(name = "OrderNumber", nullable = false)
    private int orderNumber;

    @Column(name = "PermissionJson", columnDefinition = "nvarchar(max)")
    private String permissionJson;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;
}

