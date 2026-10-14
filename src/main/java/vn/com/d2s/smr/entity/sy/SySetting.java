package vn.com.d2s.smr.entity.sy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Cấu hình hệ thống không bí mật, mỗi khóa một dòng, giá trị dạng JSON (SMR-DB §6, phase P7). */
@Getter
@Setter
@Entity
@Table(name = "SySettings")
public class SySetting {

    @Id
    @Column(name = "SettingKey", nullable = false, length = 100)
    private String key;

    @Column(name = "ValueJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String valueJson;

    @Column(name = "UpdatedBy", length = 450)
    private String updatedBy;

    @Column(name = "UpdatedAt", columnDefinition = "datetime2")
    private LocalDateTime updatedAt;
}
