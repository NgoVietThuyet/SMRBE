package vn.com.d2s.smr.dto.mt.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateTaskRequest(
        @NotBlank(message = "Tên công việc không được để trống.")
        @Size(max = 300, message = "Tên công việc không vượt quá 300 ký tự.")
        String title,
        @Size(max = 4000, message = "Mô tả công việc không vượt quá 4000 ký tự.")
        String description,
        String assigneeUserName,
        LocalDateTime dueDate,
        int status,
        int priority,
        String sourceRef,
        String parentId,
        Integer level,
        /** P12 — null = giữ nguyên. */
        LocalDateTime startDate,
        String color,
        Boolean isArchived,
        /** P12: true = xoá ngày bắt đầu/hạn (vì null nghĩa là giữ nguyên với startDate). */
        Boolean clearDates
) {
    public UpdateTaskRequest(String title, String description, String assigneeUserName, LocalDateTime dueDate,
                             int status, int priority, String sourceRef, String parentId, Integer level) {
        this(title, description, assigneeUserName, dueDate, status, priority, sourceRef, parentId, level, null, null, null, null);
    }
}
