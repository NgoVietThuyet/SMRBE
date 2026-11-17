package vn.com.d2s.smr.service.mt.pipeline.embedding;

/** Không tạo được embedding (tắt, chưa cấu hình, lỗi mạng, phản hồi sai). Người gọi rơi về tìm kiếm từ khóa. */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
