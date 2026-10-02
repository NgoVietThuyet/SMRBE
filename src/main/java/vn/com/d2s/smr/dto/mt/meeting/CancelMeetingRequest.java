package vn.com.d2s.smr.dto.mt.meeting;

import jakarta.validation.constraints.NotBlank;

public record CancelMeetingRequest(
        String meetingId,
        @NotBlank(message = "Lý do hủy không được để trống.")
        String reason
) {
}
