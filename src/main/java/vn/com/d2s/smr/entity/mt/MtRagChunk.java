package vn.com.d2s.smr.entity.mt;

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

/** Một đoạn dữ liệu cho hỏi đáp (transcript cửa sổ trượt / tài liệu theo trang) kèm vector — 010_pipeline.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtRagChunks")
public class MtRagChunk {

    public static final String TRANSCRIPT = "TRANSCRIPT";
    public static final String DOC = "DOC";

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "Kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "Title", length = 300)
    private String title;

    @Column(name = "Speaker", length = 300)
    private String speaker;

    @Column(name = "OffsetText", length = 20)
    private String offsetText;

    @Column(name = "Content", nullable = false, columnDefinition = "nvarchar(max)")
    private String content = "";

    @Column(name = "Refs", columnDefinition = "nvarchar(max)")
    private String refs;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "Embedding", columnDefinition = "varbinary(max)")
    private byte[] embedding;

    @Column(name = "Ord", nullable = false)
    private int ord;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;
}
