package vn.com.d2s.smr.service.ad.face;

/** Gọi dịch vụ phụ nhận dạng khuôn mặt (FastAPI + DeepFace). Lỗi kết nối / quá hạn → {@link FaceEngineException} (fail-closed). */
public interface FaceEngineClient {

    /**
     * @param faces     số khuôn mặt tìm thấy trong ảnh
     * @param embedding đặc trưng (chỉ khi faces == 1)
     * @param real      kết quả "mặt thật"; null nếu dịch vụ không trả (coi như không đạt)
     */
    record FaceAnalysis(int faces, float[] embedding, Boolean real, Double antispoofScore, double quality, String model, int dim) {
    }

    FaceAnalysis analyze(String imageBase64);
}
