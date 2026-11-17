package vn.com.d2s.smr.service.ad.face;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.config.FaceProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn thử sai: khóa tạm một khóa (IP hoặc tên đăng nhập) sau {@code maxFailures} lần sai liên tiếp trong
 * {@code lockMinutes}. Bộ nhớ trong → chỉ đúng khi chạy một instance BE.
 */
@Component
public class FaceRateLimiter {

    private static final class State {
        int failures;
        Instant windowStart;
        Instant lockedUntil;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final FaceProperties props;
    private final Clock clock;

    @Autowired
    public FaceRateLimiter(FaceProperties props) {
        this(props, Clock.systemUTC());
    }

    FaceRateLimiter(FaceProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Số phút còn bị khóa của khóa bị khóa lâu nhất; 0 nếu không bị khóa. */
    public long lockedMinutes(String... keys) {
        Instant now = clock.instant();
        long max = 0;
        for (String k : keys) {
            State s = states.get(k);
            if (s != null && s.lockedUntil != null && s.lockedUntil.isAfter(now)) {
                max = Math.max(max, Math.max(1, Duration.between(now, s.lockedUntil).toMinutes() + 1));
            }
        }
        return max;
    }

    public void fail(String... keys) {
        Instant now = clock.instant();
        Duration window = Duration.ofMinutes(props.lockMinutes());
        for (String k : keys) {
            states.compute(k, (key, s) -> {
                if (s == null || s.windowStart == null || s.windowStart.plus(window).isBefore(now)) {
                    s = new State();
                    s.windowStart = now;
                }
                s.failures++;
                if (s.failures >= props.maxFailures()) s.lockedUntil = now.plus(window);
                return s;
            });
        }
        if (states.size() > 50000) {
            states.values().removeIf(s -> s.lockedUntil == null && s.windowStart.plus(window).isBefore(now));
        }
    }

    public void success(String... keys) {
        for (String k : keys) states.remove(k);
    }
}
