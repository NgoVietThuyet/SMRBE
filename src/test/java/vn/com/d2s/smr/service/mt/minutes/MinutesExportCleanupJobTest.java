package vn.com.d2s.smr.service.mt.minutes;

import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinutesExportCleanupJobTest {

    private static CmFile file(String id) {
        CmFile f = new CmFile();
        f.setId(id);
        f.setObjectName("exports/minutes/m1/" + id + ".docx");
        return f;
    }

    @Test
    void removesExportsOlderThanRetentionAndSkipsBrokenOnes() {
        Instant now = Instant.parse("2026-10-14T03:00:00Z");
        CmFileRepository repo = mock(CmFileRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        CmFile ok = file("a");
        CmFile broken = file("b");
        when(repo.findByTypeAndCreateDateBefore(MinutesExportServiceImpl.FILE_TYPE_EXPORT,
                LocalDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(7))).thenReturn(List.of(broken, ok));
        doThrow(new IllegalStateException("minio")).when(storage).delete(broken.getObjectName());

        int removed = new MinutesExportCleanupJob(repo, storage, 7, Clock.fixed(now, ZoneOffset.UTC)).cleanup();

        assertThat(removed).isEqualTo(1);
        verify(repo).delete(ok);
        verify(repo, never()).delete(broken);
    }
}
