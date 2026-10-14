package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
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
    /**
     * FE gửi / nhận với tên "isFinal". Getter Lombok của trường boolean `isFinal` là isFinal() nên Jackson
     * suy ra tên "final" → trước đây mọi phụ đề từ FE bị từ chối (Unrecognized field "isFinal") và không hiển thị.
     */
    @JsonProperty("isFinal")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean isFinal;
    private Instant timestamp;

    @JsonIgnore
    public boolean isFinal() {
        return isFinal;
    }

    @JsonIgnore
    public void setFinal(boolean value) {
        this.isFinal = value;
    }
}
