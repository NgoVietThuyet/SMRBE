package vn.com.d2s.smr.dto.ad.face;

/** enabled: tính năng bật; allowed: tài khoản được dùng (tài khoản quản trị bị loại); enrolled/samples: đã đăng ký mặt chưa. */
public record FaceStatusResponse(boolean enabled, boolean allowed, boolean enrolled, int samples) {
}
