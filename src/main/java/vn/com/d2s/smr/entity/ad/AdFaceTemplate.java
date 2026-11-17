package vn.com.d2s.smr.entity.ad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một mẫu đặc trưng khuôn mặt (đã mã hóa) của người dùng — 009_face.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "AdFaceTemplates")
public class AdFaceTemplate {

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "UserName", nullable = false, length = 450)
    private String userName;

    /** nonce(12) | ciphertext | tag(16), AES-256-GCM. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "Embedding", nullable = false, columnDefinition = "varbinary(max)")
    private byte[] embedding;

    @Column(name = "Model", nullable = false, length = 50)
    private String model;

    @Column(name = "Dim", nullable = false)
    private int dim;

    @Column(name = "Quality", nullable = false)
    private double quality;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;
}
