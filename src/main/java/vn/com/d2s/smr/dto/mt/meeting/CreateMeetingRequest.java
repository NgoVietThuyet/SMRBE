package vn.com.d2s.smr.dto.mt.meeting;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public record CreateMeetingRequest(
        @NotBlank(message = "Tên cuộc họp không được để trống.")
        String name,
        String description,
        String agenda,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        String timeZone,
        int visibility,
        boolean saveAsDraft,
        boolean publishInvitation,
        MeetingSettingsDto settings,
        List<MeetingParticipantInputDto> participants,
        List<String> participantUserNames
) {
}
