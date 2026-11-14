package vn.com.d2s.smr.service.mt.minutes;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.com.d2s.smr.service.mt.MeetingEnded;

/** Cuộc họp kết thúc → hẹn tự sinh bản nháp biên bản (sau khi giao dịch kết thúc cuộc họp đã commit). */
@Component
public class MinutesAutoDraftListener {

    private final MinutesService minutesService;

    public MinutesAutoDraftListener(MinutesService minutesService) {
        this.minutesService = minutesService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMeetingEnded(MeetingEnded event) {
        minutesService.scheduleAutoDraft(event.meetingId());
    }
}
