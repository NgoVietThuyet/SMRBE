package vn.com.d2s.smr.dto.mt.task.board;

/** workId = bảng công việc chung của cuộc họp; cardId = việc vừa tạo → FE mở /tasks/{workId}?card={cardId}. */
public record MeetingCardResponse(String workId, String listId, String cardId, String title) {
}
