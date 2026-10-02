package vn.com.d2s.smr.service.mt;

public interface MeetingEventPublisher {
    void publishFilesChanged(String meetingId, String fileId, String action);
    void publishTaskChanged(String meetingId, String taskId, String action);
    void publishMeetingStatusChanged(String meetingId, String status);
    void publishCaption(String meetingId, Object captionPayload);
}

