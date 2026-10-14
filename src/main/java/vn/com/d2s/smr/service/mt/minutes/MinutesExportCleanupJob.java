package vn.com.d2s.smr.service.mt.minutes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dọn file biên bản đã dựng cho "Tải biên bản họp" (CmFiles Type = 7) quá N ngày (mặc định 7).
 * Người dùng bấm tải lại sau đó → hệ thống tổng hợp lại (job cũ không còn file nên không được dùng lại).
 * Mẫu biên bản của cuộc họp (Type = 6) không bị dọn.
 */
@Component
public class MinutesExportCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MinutesExportCleanupJob.class);

    private final CmFileRepository fileRepository;
    private final ObjectStorageService storage;
    private final int retentionDays;
    private final Clock clock;

    public MinutesExportCleanupJob(CmFileRepository fileRepository, ObjectStorageService storage,
                                   @Value("${smr.minutes.export-retention-days:7}") int retentionDays) {
        this(fileRepository, storage, retentionDays, Clock.systemUTC());
    }

    MinutesExportCleanupJob(CmFileRepository fileRepository, ObjectStorageService storage, int retentionDays, Clock clock) {
        this.fileRepository = fileRepository;
        this.storage = storage;
        this.retentionDays = Math.max(1, retentionDays);
        this.clock = clock;
    }

    @Scheduled(cron = "${smr.minutes.export-cleanup-cron:0 45 2 * * *}")
    public int cleanup() {
        List<CmFile> expired = fileRepository.findByTypeAndCreateDateBefore(MinutesExportServiceImpl.FILE_TYPE_EXPORT,
                LocalDateTime.now(clock).minusDays(retentionDays));
        int removed = 0;
        for (CmFile f : expired) {
            try {
                storage.delete(f.getObjectName());
                fileRepository.delete(f);
                removed++;
            } catch (RuntimeException e) {
                // Lỗi một file không chặn các file còn lại; lần chạy sau thử lại.
                log.warn("Không dọn được file biên bản {}: {}", f.getId(), e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("Đã dọn {} file biên bản quá {} ngày.", removed, retentionDays);
        }
        return removed;
    }
}
