package vn.com.d2s.smr.dto.mt.task;

import java.time.LocalDateTime;

public record TaskListItemResponse(
        String id,
        String meetingId,
        String meetingName,
        LocalDateTime meetingStartTime,
        String parentId,
        int level,
        int childrenCount,
        int completedChildrenCount,
        int progress,
        String title,
        String description,
        String assigneeUserName,
        String assigneeFullName,
        LocalDateTime dueDate,
        int status,
        int priority,
        boolean isPublic,
        boolean isCreator,
        boolean canEdit,
        String createBy,
        LocalDateTime createDate,
        LocalDateTime updateDate,
        String sourceRef
) {
}
