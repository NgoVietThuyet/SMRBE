package vn.com.d2s.smr.service.mt;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingAutoEndJobTest {

    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @Test
    void endsOnlyAfterRoomStaysEmptyForGracePeriod() {
        MeetingService service = mock(MeetingService.class);
        MutableClock clock = new MutableClock();
        MeetingAutoEndJob job = new MeetingAutoEndJob(service, 120, clock);
        when(service.overdueOngoingMeetingIds()).thenReturn(List.of("m1"));
        when(service.isOverdueAndEmpty("m1")).thenReturn(true);
        when(service.endIfIdle("m1")).thenReturn(true);

        job.run();                                   // t0: bắt đầu đếm
        verify(service, never()).endIfIdle("m1");
        clock.now.set(clock.now.get().plusSeconds(60));
        job.run();                                   // 60s: chưa đủ
        verify(service, never()).endIfIdle("m1");
        clock.now.set(clock.now.get().plusSeconds(60));
        job.run();                                   // 120s: kết thúc
        verify(service).endIfIdle("m1");
    }

    @Test
    void someoneReturningResetsCountdown() {
        MeetingService service = mock(MeetingService.class);
        MutableClock clock = new MutableClock();
        MeetingAutoEndJob job = new MeetingAutoEndJob(service, 120, clock);
        when(service.overdueOngoingMeetingIds()).thenReturn(List.of("m1"));
        when(service.isOverdueAndEmpty("m1")).thenReturn(true, false, true, true);

        job.run();                                   // trống: bắt đầu đếm
        clock.now.set(clock.now.get().plusSeconds(100));
        job.run();                                   // có người → reset
        clock.now.set(clock.now.get().plusSeconds(100));
        job.run();                                   // trống lại: đếm từ đầu
        clock.now.set(clock.now.get().plusSeconds(100));
        job.run();                                   // mới 100s kể từ lúc trống lại
        verify(service, never()).endIfIdle("m1");
    }
}
