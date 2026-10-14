package vn.com.d2s.smr.dto.mt.task.board;

import java.time.LocalDateTime;

public record CommentResponse(String id, String userName, String fullName, String content, LocalDateTime createDate,
                              boolean canDelete) {
}
