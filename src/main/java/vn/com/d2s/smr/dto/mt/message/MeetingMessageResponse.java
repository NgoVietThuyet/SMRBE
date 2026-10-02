package vn.com.d2s.smr.dto.mt.message;

import java.time.LocalDateTime;

public record MeetingMessageResponse(
        String id,
        String meetingId,
        String senderUserId,
        String senderFullName,
        String receiverUserId,
        String messageText,
        LocalDateTime createDate
) {
}
