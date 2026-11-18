package vn.com.d2s.smr.service.mt.impl;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.service.mt.MeetingEnded;
import vn.com.d2s.smr.service.mt.PollService;

/** Cuộc họp kết thúc → đóng mọi biểu quyết còn mở (chạy đồng bộ trong giao dịch kết thúc, trước khi chốt hồ sơ). */
@Component
public class PollCloseListener {

    private final PollService pollService;

    public PollCloseListener(PollService pollService) {
        this.pollService = pollService;
    }

    @EventListener
    public void onMeetingEnded(MeetingEnded event) {
        pollService.closeAll(event.meetingId());
    }
}
