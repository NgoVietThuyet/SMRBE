package vn.com.d2s.smr.service.ad.face;

/** Dịch vụ nhận dạng không dùng được (chưa cấu hình, lỗi kết nối, quá hạn). Không tính là lần thử sai của người dùng. */
public class FaceEngineException extends RuntimeException {
    public FaceEngineException(String message) {
        super(message);
    }

    public FaceEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
