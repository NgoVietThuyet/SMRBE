package vn.com.d2s.smr.service.mt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tự kết thúc cuộc họp: đã quá giờ kết thúc dự kiến VÀ không còn ai trong phòng.
 * Phòng phải trống liên tục ít nhất {@code smr.meeting.auto-end-grace-seconds} (mặc định 120s) mới kết thúc,
 * để không kết thúc nhầm khi mọi người đang nối lại hub (rớt mạng / khởi động lại BE).
 */
@Component
public class MeetingAutoEndJob {

    private static final Logger log = LoggerFactory.getLogger(MeetingAutoEndJob.class);

    private final MeetingService meetingService;
    private final Duration grace;
    private final Clock clock;
    /** meetingId → lần đầu job thấy cuộc họp quá giờ mà phòng trống. */
    private final Map<String, Instant> emptySince = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public MeetingAutoEndJob(MeetingService meetingService,
                             @Value("${smr.meeting.auto-end-grace-seconds:120}") long graceSeconds) {
        this(meetingService, graceSeconds, Clock.systemUTC());
    }

    MeetingAutoEndJob(MeetingService meetingService, long graceSeconds, Clock clock) {
        this.meetingService = meetingService;
        this.grace = Duration.ofSeconds(Math.max(0, graceSeconds));
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smr.meeting.auto-end-interval-ms:30000}", initialDelayString = "${smr.meeting.auto-end-interval-ms:30000}")
    public void run() {
        try {
            List<String> overdue = meetingService.overdueOngoingMeetingIds();
            Instant now = clock.instant();
            Set<String> seen = new HashSet<>(overdue);
            emptySince.keySet().retainAll(seen);
            for (String id : overdue) {
                if (!meetingService.isOverdueAndEmpty(id)) {
                    emptySince.remove(id); // còn người trong phòng → đếm lại từ đầu
                    continue;
                }
                Instant since = emptySince.putIfAbsent(id, now);
                if (since == null) since = now;
                if (Duration.between(since, now).compareTo(grace) < 0) continue;
                if (meetingService.endIfIdle(id)) {
                    log.info("Đã tự kết thúc cuộc họp {} (quá giờ dự kiến, không còn ai trong phòng).", id);
                }
                emptySince.remove(id);
            }
        } catch (RuntimeException e) {
            log.warn("Tự kết thúc cuộc họp lỗi: {}", e.getMessage());
        }
    }
}
