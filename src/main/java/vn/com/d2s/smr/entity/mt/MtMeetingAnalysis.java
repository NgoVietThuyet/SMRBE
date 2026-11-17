package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Kết quả phân tích sau họp (từ điển, transcript đã làm sạch, tóm tắt tài liệu) — 010_pipeline.sql. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MtMeetingAnalyses")
public class MtMeetingAnalysis {

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "SourceHash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "GlossaryJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String glossaryJson = "{}";

    @Column(name = "DocDigestJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String docDigestJson = "[]";

    @Column(name = "CleanedJson", nullable = false, columnDefinition = "nvarchar(max)")
    private String cleanedJson = "[]";

    @Column(name = "Model", length = 100)
    private String model;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime updatedAt;
}
