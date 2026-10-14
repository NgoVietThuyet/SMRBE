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
        String notes,
        /** true: phát hành bản nháp (trạng thái 0 → 1) và gửi lời mời. */
        boolean publish
) {
    public UpdateMeetingRequest(String id, String name, String description, String agenda,
                                LocalDateTime expectedStartTime, LocalDateTime expectedEndTime, String timeZone,
                                int visibility, MeetingSettingsDto settings, String rowVersion,
                                String meetContent, String notes) {
        this(id, name, description, agenda, expectedStartTime, expectedEndTime, timeZone, visibility, settings,
                rowVersion, meetContent, notes, false);
    }
}
