package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record MeetingDetailResponse(
        String id,
        String name,
        String description,
        LocalDateTime expectedStartTime,
        LocalDateTime expectedEndTime,
        int status,
        int visibility,
        String roomCode,
        String joinUrl,
        int participantCount,
        @JsonProperty("isHost") boolean host,
        String hostName,
        String agenda,
        String timeZone,
        String cancellationReason,
        MeetingSettingsDto settings,
        List<MeetingParticipantResponse> participants,
        List<MeetingAuditResponse> activity,
        String rowVersion,
        boolean canManage,
        /** Giờ bắt đầu / kết thúc thực tế (StartDate / EndDate), null nếu chưa diễn ra. */
        LocalDateTime actualStartTime,
        LocalDateTime actualEndTime
) {
    public MeetingDetailResponse(String id, String name, String description, LocalDateTime expectedStartTime,
                                 LocalDateTime expectedEndTime, int status, int visibility, String roomCode,
                                 String joinUrl, int participantCount, boolean host, String hostName, String agenda,
                                 String timeZone, String cancellationReason, MeetingSettingsDto settings,
                                 List<MeetingParticipantResponse> participants, List<MeetingAuditResponse> activity,
                                 String rowVersion, boolean canManage) {
        this(id, name, description, expectedStartTime, expectedEndTime, status, visibility, roomCode, joinUrl,
                participantCount, host, hostName, agenda, timeZone, cancellationReason, settings, participants,
                activity, rowVersion, canManage, null, null);
    }
}
