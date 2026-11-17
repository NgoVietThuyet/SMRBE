package vn.com.d2s.smr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Dịch vụ tạo embedding (smr.embedding.*): POST {url} {"input":[...],"model":"..."} → [{"index":0,"embedding":[[...]]}]. */
@Validated
@ConfigurationProperties(prefix = "smr.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String url,
        String apiKey,
        String model,
        int connectTimeoutMs,
        int readTimeoutMs,
        int batchSize
) {
    public EmbeddingProperties {
        if (url == null) url = "";
        if (apiKey == null) apiKey = "";
        if (model == null || model.isBlank()) model = "default";
        if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
        if (readTimeoutMs <= 0) readTimeoutMs = 30000;
        if (batchSize <= 0) batchSize = 16;
    }
}
