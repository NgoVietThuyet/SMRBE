package vn.com.d2s.smr.service.nt.event;

import java.util.List;

/**
 * Domain event do MeetingService / TaskService phát trong transaction nghiệp vụ.
 * Service nghiệp vụ không biết gì về thông báo; NotificationDispatcher nghe và
 * tự tính người nhận theo quyền thật tại thời điểm sự kiện.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** Người dùng vừa được thêm vào cuộc họp (tạo mới hoặc thêm thành viên). */
    public record MeetingInvited(String meetingId, String actor, List<String> userNames) {
    }

    /** Giờ bắt đầu/kết thúc cuộc họp thay đổi. */
    public record MeetingRescheduled(String meetingId, String actor) {
    }

    public record MeetingCancelled(String meetingId, String actor, String reason) {
    }

    public record MeetingStarted(String meetingId, String actor) {
    }

    /** meetingName truyền kèm vì người nhận đã mất quyền xem cuộc họp. */
    public record MeetingParticipantRemoved(String meetingId, String meetingName, String actor, String userName) {
    }

    /** Người phụ trách của task đổi thành assignee (khác null). */
    public record TaskAssigned(String taskId, String actor, String assignee) {
    }

    public record TaskStatusChanged(String taskId, String actor, int oldStatus, int newStatus) {
    }

    public record TaskShared(String taskId, String actor, String userName) {
    }

    /** P13: AI sinh xong bản nháp biên bản → báo chủ trì / thư ký (recipients). */
    public record MinutesReady(String meetingId, String actor, int version, List<String> recipients) {
    }

    /** P13: biên bản được phát hành → báo mọi thành viên. */
    public record MinutesPublished(String meetingId, String actor, int version) {
    }
}
