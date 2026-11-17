package vn.com.d2s.smr.dto.ad.face;

public record FaceActionResult(boolean success, String message) {
    public static FaceActionResult ok(String message) {
        return new FaceActionResult(true, message);
    }

    public static FaceActionResult failure(String message) {
        return new FaceActionResult(false, message);
    }
}
