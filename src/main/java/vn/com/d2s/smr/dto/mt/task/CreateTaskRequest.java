package vn.com.d2s.smr.dto.mt.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateTaskRequest(
        String meetingId,
        String parentId,
        int level,
        @NotBlank(message = "Tên công việc không được để trống.")
        @Size(max = 300, message = "Tên công việc không vượt quá 300 ký tự.")
        String title,
        @Size(max = 4000, message = "Mô tả công việc không vượt quá 4000 ký tự.")
        String description,
        String assigneeUserName,
        LocalDateTime dueDate,
        int status,
        int priority,
        boolean isPublic,
        String sourceRef
) {
}
