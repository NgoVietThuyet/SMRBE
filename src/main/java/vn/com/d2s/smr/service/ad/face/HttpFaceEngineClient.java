package vn.com.d2s.smr.service.ad.face;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import vn.com.d2s.smr.config.FaceProperties;

import java.util.Map;

/** Gọi POST {url}/represent của face-service. Mẫu giống {@code LlmClientServiceImpl} (RestTemplate + timeout). */
@Component
public class HttpFaceEngineClient implements FaceEngineClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFaceEngineClient.class);

    private final FaceProperties props;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    public HttpFaceEngineClient(FaceProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public FaceAnalysis analyze(String imageBase64) {
        if (props.apiKey().isBlank()) {
            throw new FaceEngineException("Chưa cấu hình FACE_API_KEY.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", props.apiKey());
        String body;
        try {
            body = restTemplate.postForObject(props.url() + "/represent",
                    new HttpEntity<>(Map.of("image", imageBase64), headers), String.class);
        } catch (RestClientException e) {
            log.warn("face-service lỗi: {}", e.getMessage());
            throw new FaceEngineException("Dịch vụ nhận dạng khuôn mặt không phản hồi.", e);
        }
        if (body == null || body.isBlank()) {
            throw new FaceEngineException("Dịch vụ nhận dạng khuôn mặt trả về rỗng.");
        }
        try {
            JsonNode n = mapper.readTree(body);
            int faces = n.path("faces").asInt(0);
            float[] emb = null;
            JsonNode e = n.path("embedding");
            if (e.isArray()) {
                emb = new float[e.size()];
                for (int i = 0; i < emb.length; i++) emb[i] = (float) e.get(i).asDouble();
            }
            Boolean real = n.hasNonNull("isReal") ? n.get("isReal").asBoolean() : null;
            Double score = n.hasNonNull("antispoofScore") ? n.get("antispoofScore").asDouble() : null;
            return new FaceAnalysis(faces, emb, real, score, n.path("quality").asDouble(0),
                    n.path("model").asText(""), n.path("dim").asInt(emb == null ? 0 : emb.length));
        } catch (Exception ex) {
            throw new FaceEngineException("Không đọc được kết quả từ dịch vụ nhận dạng khuôn mặt.", ex);
        }
    }
}
