package vn.com.d2s.smr.dto.mt.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeetingSettingsDto(
        int schemaVersion,
        boolean lobbyEnabled,
        boolean allowGuests,
        boolean allowJoinBeforeHost,
        boolean chatEnabled,
        boolean screenShareEnabled,
        boolean whiteboardEnabled,
        boolean fileUploadEnabled,
        boolean recordingEnabled,
        boolean captionsEnabled,
        boolean aiMinutesEnabled,
        String minutesTemplateId,
        String passwordHash,
        @JsonProperty("hasPassword") boolean hasPassword
) {
    public static MeetingSettingsDto defaultSettings() {
        return new MeetingSettingsDto(
                1, false, true, false, true, true, true, true, true, false, true, "", "", false
        );
    }
}
