package vn.com.d2s.smr.service.mt.pipeline.embedding;

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
import vn.com.d2s.smr.config.EmbeddingProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gọi dịch vụ embedding: body {"input":["...","..."],"model":"default"} (hỗ trợ mảng), trả
 * [{"index":0,"embedding":[[...]]}] (embedding có thể lồng một lớp). Vector được chuẩn hóa độ dài 1.
 */
@Component
public class HttpEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(HttpEmbeddingClient.class);
    static final int MAX_TEXT_CHARS = 2000;

    private final EmbeddingProperties props;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    public HttpEmbeddingClient(EmbeddingProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean available() {
        return props.enabled() && !props.url().isBlank();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (!available()) throw new EmbeddingException("Dịch vụ embedding chưa bật.");
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += props.batchSize()) {
            List<String> batch = texts.subList(i, Math.min(i + props.batchSize(), texts.size())).stream()
                    .map(t -> t.length() > MAX_TEXT_CHARS ? t.substring(0, MAX_TEXT_CHARS) : t).toList();
            out.addAll(callBatch(batch));
        }
        return out;
    }

    private List<float[]> callBatch(List<String> batch) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!props.apiKey().isBlank()) headers.setBearerAuth(props.apiKey());
        String body;
        try {
            body = restTemplate.postForObject(props.url(),
                    new HttpEntity<>(Map.of("input", batch, "model", props.model()), headers), String.class);
        } catch (RestClientException e) {
            log.warn("Dịch vụ embedding lỗi: {}", e.getMessage());
            throw new EmbeddingException("Dịch vụ embedding không phản hồi.", e);
        }
        return parse(body, batch.size(), mapper);
    }

    /** Đọc phản hồi; sắp theo index; kiểm tra đủ số vector. Tách static để test. */
    static List<float[]> parse(String body, int expected, ObjectMapper mapper) {
        if (body == null || body.isBlank()) throw new EmbeddingException("Dịch vụ embedding trả về rỗng.");
        try {
            JsonNode root = mapper.readTree(body);
            TreeMap<Integer, float[]> byIndex = new TreeMap<>();
            if (root.isArray()) {
                int pos = 0;
                for (JsonNode item : root) {
                    JsonNode e = item.path("embedding");
                    if (e.isArray() && e.size() > 0 && e.get(0).isArray()) e = e.get(0);
                    if (!e.isArray() || e.isEmpty()) throw new EmbeddingException("Phản hồi embedding thiếu vector.");
                    float[] v = new float[e.size()];
                    for (int k = 0; k < v.length; k++) v[k] = (float) e.get(k).asDouble();
                    byIndex.put(item.path("index").asInt(pos), VectorCodec.normalize(v));
                    pos++;
                }
            } else if (root.path("data").isArray()) { // dạng OpenAI {"data":[{"index":0,"embedding":[...]}]}
                int pos = 0;
                for (JsonNode item : root.path("data")) {
                    JsonNode e = item.path("embedding");
                    float[] v = new float[e.size()];
                    for (int k = 0; k < v.length; k++) v[k] = (float) e.get(k).asDouble();
                    byIndex.put(item.path("index").asInt(pos), VectorCodec.normalize(v));
                    pos++;
                }
            } else {
                throw new EmbeddingException("Phản hồi embedding không đúng định dạng.");
            }
            if (byIndex.size() != expected) throw new EmbeddingException("Số vector trả về (" + byIndex.size() + ") khác số văn bản (" + expected + ").");
            return new ArrayList<>(byIndex.values());
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Không đọc được phản hồi embedding: " + e.getMessage(), e);
        }
    }
}
