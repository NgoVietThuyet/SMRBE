package vn.com.d2s.smr.dto.mt.task.board;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/** Modal chi tiết việc (GET Task/{id}/card). */
public record CardDetailResponse(
        BoardCardResponse card,
        String workId,
        String listTitle,
        String createByFullName,
        LocalDateTime createDate,
        String sourceRef,
        List<LabelResponse> labels,
        List<ChecklistItemResponse> checklist,
        List<CommentResponse> comments,
        List<TaskFileResponse> files,
        boolean canEdit,
        @JsonProperty("isOwner") boolean owner
) {
}
