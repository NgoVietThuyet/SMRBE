package vn.com.d2s.smr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Đăng nhập bằng khuôn mặt (smr.face.*). Mặc định TẮT. Ngưỡng là độ giống cosine (0..1, càng cao càng giống) và
 * phải chỉnh bằng dữ liệu thật của đơn vị.
 */
@Validated
@ConfigurationProperties(prefix = "smr.face")
public record FaceProperties(
        boolean enabled,
        String url,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs,
        double threshold,
        double margin,
        double sampleAgreement,
        double minLiveness,
        double minQuality,
        int minFrames,
        int maxFrames,
        int maxEnrollFrames,
        int challengeTtlSeconds,
        int maxFailures,
        int lockMinutes,
        String encKey
) {
    public FaceProperties {
        if (url == null || url.isBlank()) url = "http://smr-face:8000";
        if (apiKey == null) apiKey = "";
        if (encKey == null) encKey = "";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
        if (readTimeoutMs <= 0) readTimeoutMs = 20000;
        if (threshold <= 0) threshold = 0.45;
        if (margin <= 0) margin = 0.05;
        if (sampleAgreement <= 0) sampleAgreement = 0.5;
        if (minLiveness <= 0) minLiveness = 0.6;
        if (minQuality <= 0) minQuality = 0.3;
        if (minFrames <= 0) minFrames = 2;
        if (maxFrames <= 0) maxFrames = 4;
        if (maxEnrollFrames <= 0) maxEnrollFrames = 6;
        if (challengeTtlSeconds <= 0) challengeTtlSeconds = 60;
        if (maxFailures <= 0) maxFailures = 5;
        if (lockMinutes <= 0) lockMinutes = 15;
    }
}
