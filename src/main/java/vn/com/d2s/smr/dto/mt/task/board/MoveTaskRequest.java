package vn.com.d2s.smr.dto.mt.task.board;

/**
 * Kéo thả: parentId = cột đích (thẻ) hoặc chính công việc cấp 0 (cột);
 * beforeId = đặt trước phần tử này, null = cuối danh sách.
 */
public record MoveTaskRequest(String parentId, String beforeId) {
}
