package vn.com.d2s.smr.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeTest {

    @Test
    void vietnamDayStartsAtSeventeenUtcOfPreviousDay() {
        assertThat(BusinessTime.startOfDayUtc(LocalDate.of(2026, 10, 9)))
                .isEqualTo(LocalDateTime.of(2026, 10, 8, 17, 0));
    }

    @Test
    void earlyMorningInVietnamIsAlreadyTheNextDay() {
        // 06:59 giờ VN ngày 09/10 = 23:59 UTC ngày 08/10 → ngày làm việc vẫn là 09/10.
        Clock beforeSeven = Clock.fixed(Instant.parse("2026-10-08T23:59:00Z"), ZoneOffset.UTC);
        assertThat(BusinessTime.today(beforeSeven)).isEqualTo(LocalDate.of(2026, 10, 9));

        // 07:00 giờ VN ngày 09/10 = 00:00 UTC ngày 09/10.
        Clock atSeven = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC);
        assertThat(BusinessTime.today(atSeven)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(BusinessTime.startOfTodayUtc(atSeven)).isEqualTo(LocalDateTime.of(2026, 10, 8, 17, 0));
    }
}
