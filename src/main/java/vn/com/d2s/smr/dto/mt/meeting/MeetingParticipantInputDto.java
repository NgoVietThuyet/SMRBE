package vn.com.d2s.smr.dto.mt.meeting;

import jakarta.validation.constraints.NotBlank;

public record MeetingParticipantInputDto(
        @NotBlank(message = "Username không được để trống.")
        String userName,
        int role
) {
}
