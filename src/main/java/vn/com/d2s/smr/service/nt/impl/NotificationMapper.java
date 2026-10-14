package vn.com.d2s.smr.service.nt.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.com.d2s.smr.dto.nt.NotificationItemResponse;
import vn.com.d2s.smr.entity.nt.Notification;

import java.time.ZoneOffset;

final class NotificationMapper {

    private NotificationMapper() {
    }

    static NotificationItemResponse toResponse(Notification n, ObjectMapper objectMapper) {
        return new NotificationItemResponse(
                n.getId(),
                n.getType(),
                n.getSeverity(),
                n.getTitle(),
                n.getBody(),
                n.getResourceType(),
                n.getResourceId(),
                n.getMeetingId(),
                n.getActorUserName(),
                n.getActorFullName(),
                parse(n.getDataJson(), objectMapper),
                n.isRead(),
                n.getCreateDate() != null ? n.getCreateDate().toInstant(ZoneOffset.UTC) : null
        );
    }

    private static JsonNode parse(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null; // dữ liệu phụ hỏng không được làm hỏng cả danh sách
        }
    }
}
