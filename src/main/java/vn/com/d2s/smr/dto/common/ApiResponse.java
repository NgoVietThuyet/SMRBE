package vn.com.d2s.smr.dto.common;

public record ApiResponse<T>(boolean status, String message, T data) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, message, null);
    }
}

