package vn.com.d2s.smr.entity.mt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một đoạn transcript (câu phụ đề cuối) — 007_minutes.sql. SpokenAt theo UTC. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MeetingTranscriptSegments")
public class MeetingTranscriptSegment {

    @Id
    @Column(name = "Id", nullable = false, length = 100)
    private String id;

    @Column(name = "MeetingId", nullable = false, length = 450)
    private String meetingId;

    @Column(name = "SpeakerUserName", length = 450)
    private String speakerUserName;

    @Column(name = "SpeakerName", length = 200)
    private String speakerName;

    @Column(name = "Text", nullable = false, columnDefinition = "nvarchar(max)")
    private String text = "";

    @Column(name = "Language", length = 10)
    private String language;

    @Column(name = "SpokenAt", nullable = false, columnDefinition = "datetime2")
    private LocalDateTime spokenAt;

    @Column(name = "EditedBy", length = 450)
    private String editedBy;

    @Column(name = "EditedAt", columnDefinition = "datetime2")
    private LocalDateTime editedAt;
}
