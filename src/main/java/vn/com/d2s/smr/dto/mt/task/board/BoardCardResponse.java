package vn.com.d2s.smr.dto.mt.task.board;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/** Một thẻ (việc nhỏ cấp 2) kèm các con số hiện trên badge. canWork = được tick / checklist / bình luận / đính kèm. */
public record BoardCardResponse(
        String id,
        String parentId,
        String title,
        String description,
        String assigneeUserName,
        String assigneeFullName,
        LocalDateTime startDate,
        LocalDateTime dueDate,
        int status,
        int priority,
        int sortOrder,
        @JsonProperty("isArchived") boolean archived,
        List<String> labelIds,
        int checklistDone,
        int checklistTotal,
        int commentCount,
        int fileCount,
        String createBy,
        boolean canWork
) {
}
