package vn.com.d2s.smr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smr.llm")
public record LlmProperties(
        String url,
        String apiKey,
        String model,
        boolean bypassAuth,
        String systemPrompt,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public LlmProperties {
        if (url == null || url.isBlank()) {
            url = "http://115.146.121.185:8311/v1/chat/completions";
        }
        if (apiKey == null) {
            apiKey = "d2s_llm_test";
        }
        if (model == null || model.isBlank()) {
            model = "default";
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "Bạn là AI của hệ thống TMS hỗ trợ người dùng về mọi thứ liên quan đến cuộc họp";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 30000;
        }
    }
}
