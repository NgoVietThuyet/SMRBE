package vn.com.d2s.smr.service.ad.face;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.config.FaceProperties;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thử thách quét mặt: mã dùng MỘT lần, hết hạn nhanh, gắn với IP. Lưu trong bộ nhớ → chỉ đúng khi chạy một instance BE.
 */
@Component
public class FaceChallengeStore {

    public static final List<String> GESTURES = List.of("BLINK", "TURN_LEFT", "TURN_RIGHT");
    private static final int MAX_PENDING = 20000;

    public record Challenge(String id, String gesture, int ttlSeconds) {
    }

    private record Entry(String ip, Instant expiresAt) {
    }

    private final Map<String, Entry> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final FaceProperties props;
    private final Clock clock;

    @Autowired
    public FaceChallengeStore(FaceProperties props) {
        this(props, Clock.systemUTC());
    }

    FaceChallengeStore(FaceProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public Challenge issue(String ip) {
        purgeExpired();
        if (pending.size() >= MAX_PENDING) {
            throw new FaceEngineException("Hệ thống đang bận, vui lòng thử lại sau.");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        pending.put(id, new Entry(ip, clock.instant().plusSeconds(props.challengeTtlSeconds())));
        return new Challenge(id, GESTURES.get(random.nextInt(GESTURES.size())), props.challengeTtlSeconds());
    }

    /** Dùng mã: đúng, còn hạn, cùng IP → true và mã bị xóa (không dùng lại được). */
    public boolean consume(String id, String ip) {
        if (id == null) return false;
        Entry e = pending.remove(id);
        return e != null && e.expiresAt().isAfter(clock.instant()) && e.ip().equals(ip);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        pending.values().removeIf(e -> !e.expiresAt().isAfter(now));
    }
}
