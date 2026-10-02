package vn.com.d2s.smr.dto.mt.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingCaptionDto {
    private String id;
    private String meetingId;
    private String speakerId;
    private String speakerUserName;
    private String speakerName;
    private String text;
    private String language; // "vi-VN" | "en-US"
    private boolean isFinal;
    private Instant timestamp;
}
