package vn.com.d2s.smr.service.nt.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.repository.nt.NotificationRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/** Dọn thông báo: đã đọc quá N ngày (mặc định 90), mọi thông báo quá M ngày (mặc định 180). */
@Component
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationRepository repository;
    private final int readRetentionDays;
    private final int maxRetentionDays;
    private final Clock clock = Clock.systemUTC();

    public NotificationCleanupJob(
            NotificationRepository repository,
            @Value("${smr.notification.read-retention-days:90}") int readRetentionDays,
            @Value("${smr.notification.max-retention-days:180}") int maxRetentionDays
    ) {
        this.repository = repository;
        this.readRetentionDays = readRetentionDays;
        this.maxRetentionDays = maxRetentionDays;
    }

    @Scheduled(cron = "${smr.notification.cleanup-cron:0 30 2 * * *}")
    @Transactional
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(clock);
        int removed = repository.deleteExpired(now.minusDays(readRetentionDays), now.minusDays(maxRetentionDays));
        if (removed > 0) {
            log.info("Đã dọn {} thông báo hết hạn lưu.", removed);
        }
    }
}
