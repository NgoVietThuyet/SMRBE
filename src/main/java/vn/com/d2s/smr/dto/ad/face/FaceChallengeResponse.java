package vn.com.d2s.smr.dto.ad.face;

/** Thử thách quét mặt: mã dùng một lần + cử chỉ cần làm (BLINK, TURN_LEFT, TURN_RIGHT). */
public record FaceChallengeResponse(boolean enabled, String challengeId, String gesture, int ttlSeconds) {
}
