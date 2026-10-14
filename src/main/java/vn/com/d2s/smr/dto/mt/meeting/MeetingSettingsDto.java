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
    /**
     * Đọc SettingsJson của MeetingInfos. JSON cũ / họp nhanh chỉ có {"schemaVersion":1} → các trường thiếu
     * lấy theo {@link #defaultSettings()} (không để boolean mặc định false làm tắt chat, chia sẻ màn hình...).
     */
    public static MeetingSettingsDto fromJson(String json, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return defaultSettings();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode stored = mapper.readTree(json);
            com.fasterxml.jackson.databind.node.ObjectNode merged = mapper.valueToTree(defaultSettings());
            if (stored != null && stored.isObject()) {
                merged.setAll((com.fasterxml.jackson.databind.node.ObjectNode) stored);
            }
            return mapper.treeToValue(merged, MeetingSettingsDto.class);
        } catch (Exception e) {
            return defaultSettings();
        }
    }

    public static MeetingSettingsDto defaultSettings() {
        return new MeetingSettingsDto(
                1, false, true, false, true, true, true, true, true, false, true, "", "", false
        );
    }
}
