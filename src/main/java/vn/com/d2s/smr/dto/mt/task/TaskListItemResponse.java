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
        String sourceRef,
        /** P12 */
        LocalDateTime startDate,
        int sortOrder,
        String color,
        @com.fasterxml.jackson.annotation.JsonProperty("isArchived") boolean archived,
        /** P12: công việc cấp 0 — số việc nhỏ và số đã xong trong cả bảng. */
        int cardCount,
        int cardDoneCount
) {
    public TaskListItemResponse(String id, String meetingId, String meetingName, LocalDateTime meetingStartTime,
                                String parentId, int level, int childrenCount, int completedChildrenCount, int progress,
                                String title, String description, String assigneeUserName, String assigneeFullName,
                                LocalDateTime dueDate, int status, int priority, boolean isPublic, boolean isCreator,
                                boolean canEdit, String createBy, LocalDateTime createDate, LocalDateTime updateDate,
                                String sourceRef) {
        this(id, meetingId, meetingName, meetingStartTime, parentId, level, childrenCount, completedChildrenCount, progress,
                title, description, assigneeUserName, assigneeFullName, dueDate, status, priority, isPublic, isCreator,
                canEdit, createBy, createDate, updateDate, sourceRef, null, 0, null, false, 0, 0);
    }
}
