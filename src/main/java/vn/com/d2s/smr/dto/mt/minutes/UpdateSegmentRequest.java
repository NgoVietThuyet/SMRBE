package vn.com.d2s.smr.dto.mt.minutes;

/** Sửa đoạn transcript (chủ trì / thư ký): null = giữ nguyên. */
public record UpdateSegmentRequest(String text, String speakerName) {
}
