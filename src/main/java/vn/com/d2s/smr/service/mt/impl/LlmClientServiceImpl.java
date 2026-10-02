package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import vn.com.d2s.smr.config.LlmProperties;
import vn.com.d2s.smr.service.mt.LlmClientService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmClientServiceImpl implements LlmClientService {

    private static final Logger log = LoggerFactory.getLogger(LlmClientServiceImpl.class);

    private final LlmProperties llmProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmClientServiceImpl(LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(llmProperties.connectTimeoutMs());
        factory.setReadTimeout(llmProperties.readTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String generateAnswer(String systemPrompt, String userPrompt) {
        String url = llmProperties.url();
        log.info("Gửi yêu cầu tới LLM endpoint: {}", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (!llmProperties.bypassAuth() && llmProperties.apiKey() != null && !llmProperties.apiKey().isBlank()) {
                headers.setBearerAuth(llmProperties.apiKey());
            } else if (llmProperties.apiKey() != null && !llmProperties.apiKey().isBlank()) {
                // Kể cả khi bypassAuth=true nhưng có ApiKey thì vẫn có thể đính kèm header dự phòng
                headers.set("Authorization", "Bearer " + llmProperties.apiKey());
            }

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", llmProperties.model());
            requestPayload.put("messages", messages);
            requestPayload.put("temperature", 0.5);
            requestPayload.put("max_tokens", 2000);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);
            String responseStr = restTemplate.postForObject(url, entity, String.class);

            if (responseStr != null && !responseStr.isBlank()) {
                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).path("message");
                    String content = messageNode.path("content").asText(null);
                    if (content != null && !content.isBlank()) {
                        return content.trim();
                    }
                }
            }

            log.warn("Phản hồi từ LLM không chứa choices hợp lệ: {}", responseStr);
            return null;
        } catch (RestClientException e) {
            log.error("Lỗi khi kết nối hoặc gửi request tới LLM ({}): {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gọi LLM: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String testAnswer = generateAnswer("Respond with 'OK'", "ping");
            return testAnswer != null;
        } catch (Exception e) {
            return false;
        }
    }
}
