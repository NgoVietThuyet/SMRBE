package vn.com.d2s.smr.service.mt.pipeline.embedding;

import java.util.List;

public interface EmbeddingClient {

    /** Dịch vụ đã bật và đã cấu hình. */
    boolean available();

    /** Vector chuẩn hóa độ dài 1, đúng thứ tự đầu vào. Lỗi → {@link EmbeddingException}. */
    List<float[]> embed(List<String> texts);

    default float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }
}
