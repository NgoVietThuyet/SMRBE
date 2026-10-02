package vn.com.d2s.smr.dto.ad.auth;

public record AuthActionResult(boolean success, String message) {
    public static AuthActionResult success(String message) {
        return new AuthActionResult(true, message);
    }

    public static AuthActionResult failure(String message) {
        return new AuthActionResult(false, message);
    }
}
