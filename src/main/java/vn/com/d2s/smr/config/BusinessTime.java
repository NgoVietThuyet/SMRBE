package vn.com.d2s.smr.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Mốc "ngày" theo giờ làm việc (Asia/Bangkok, UTC+7) quy đổi về UTC.
 * Mọi LocalDateTime trong DB đang lưu theo UTC (FE gửi toISOString(), BE dùng Clock.systemUTC()),
 * nên "hôm nay" phải là [00:00 giờ VN, 24:00 giờ VN) đổi sang UTC — không phải ngày UTC.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    private BusinessTime() {
    }

    /** Ngày hiện tại theo giờ Việt Nam. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** 00:00 giờ Việt Nam của ngày {@code date}, biểu diễn theo UTC (khớp cách lưu trong DB). */
    public static LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** 00:00 giờ Việt Nam hôm nay, theo UTC. */
    public static LocalDateTime startOfTodayUtc(Clock clock) {
        return startOfDayUtc(today(clock));
    }
}
