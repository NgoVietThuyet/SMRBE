package vn.com.d2s.smr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "CreateBy", nullable = false, columnDefinition = "nvarchar(max)")
    private String createBy = "";

    @Column(name = "CreateDate", columnDefinition = "datetime2")
    private LocalDateTime createDate;

    @Column(name = "UpdateBy", nullable = false, columnDefinition = "nvarchar(max)")
    private String updateBy = "";

    @Column(name = "UpdateDate", columnDefinition = "datetime2")
    private LocalDateTime updateDate;
}

