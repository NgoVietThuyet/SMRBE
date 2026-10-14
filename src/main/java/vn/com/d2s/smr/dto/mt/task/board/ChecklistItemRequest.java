package vn.com.d2s.smr.dto.mt.task.board;

/** Thêm mục: content bắt buộc. Sửa mục: trường null = giữ nguyên. */
public record ChecklistItemRequest(String content, Boolean done) {
}
