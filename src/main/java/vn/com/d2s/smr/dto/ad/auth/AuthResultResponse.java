package vn.com.d2s.smr.dto.ad.auth;

public record AuthResultResponse(
        boolean success,
        String message,
        String token,
        String userName,
        String fullName,
        String email,
        boolean mustChangePassword,
        String refreshToken
) {
    public static AuthResultResponse failure(String message) {
        return new AuthResultResponse(false, message, null, null, null, null, false, null);
    }
}

