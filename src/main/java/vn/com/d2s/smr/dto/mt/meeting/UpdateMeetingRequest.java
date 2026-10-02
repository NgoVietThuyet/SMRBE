package vn.com.d2s.smr.dto.mt.meeting;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record UpdateMeetingRequest(
        String id,
        @NotBlank(message = "Tên cuộc họp không được để trống.")
        String name,
        String description,
        String agenda,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        String timeZone,
        int visibility,
        MeetingSettingsDto settings,
        String rowVersion,
        String meetContent,
        String notes
) {
}
