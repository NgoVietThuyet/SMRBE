package vn.com.d2s.smr.service.mt.impl;

import org.springframework.stereotype.Component;
import vn.com.d2s.smr.service.mt.MeetingEventPublisher;

@Component
public class NoOpMeetingEventPublisher implements MeetingEventPublisher {

    @Override
    public void publishFilesChanged(String meetingId, String fileId, String action) {
        // No-op fallback until SignalR / WebSocket hub adapter is connected
    }

    @Override
    public void publishTaskChanged(String meetingId, String taskId, String action) {
        // No-op fallback
    }

    @Override
    public void publishMeetingStatusChanged(String meetingId, String status) {
        // No-op fallback
    }

    @Override
    public void publishCaption(String meetingId, Object captionPayload) {
        // No-op fallback
    }
}
