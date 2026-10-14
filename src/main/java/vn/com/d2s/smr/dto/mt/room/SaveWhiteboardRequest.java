package vn.com.d2s.smr.dto.mt.room;

/** Lưu bảng trắng: baseRevision phải bằng Revision hiện tại, nếu không trả 409 (xung đột). */
public record SaveWhiteboardRequest(
        int baseRevision,
        String contentJson
) {
}
