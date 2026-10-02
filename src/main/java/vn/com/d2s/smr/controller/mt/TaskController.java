package vn.com.d2s.smr.controller.mt;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.mt.task.CreateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.TaskDetailResponse;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;
import vn.com.d2s.smr.dto.mt.task.TaskSearchRequest;
import vn.com.d2s.smr.dto.mt.task.TaskSearchResultResponse;
import vn.com.d2s.smr.dto.mt.task.TaskShareInputRequest;
import vn.com.d2s.smr.dto.mt.task.TaskVisibilityRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskStatusRequest;
import vn.com.d2s.smr.service.mt.TaskService;

import java.security.Principal;

@RestController
@RequestMapping("/api/Task")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<TaskSearchResultResponse>> query(
            Principal principal,
            @RequestBody TaskSearchRequest request
    ) {
        TaskSearchResultResponse result = taskService.searchTasks(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(result, null));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> detail(
            Principal principal,
            @PathVariable String taskId
    ) {
        try {
            TaskDetailResponse detail = taskService.getTask(principal.getName(), taskId);
            return ResponseEntity.ok(ApiResponse.success(detail, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskListItemResponse>> create(
            Principal principal,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        try {
            TaskListItemResponse created = taskService.createTask(principal.getName(), request);
            return ResponseEntity.ok(ApiResponse.success(created, "Tạo công việc thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskListItemResponse>> update(
            Principal principal,
            @PathVariable String taskId,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        try {
            TaskListItemResponse updated = taskService.updateTask(principal.getName(), taskId, request);
            return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật công việc thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<ApiResponse<TaskListItemResponse>> updateStatus(
            Principal principal,
            @PathVariable String taskId,
            @RequestBody UpdateTaskStatusRequest request
    ) {
        try {
            TaskListItemResponse updated = taskService.updateTaskStatus(principal.getName(), taskId, request);
            return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật trạng thái công việc thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskListItemResponse>> delete(
            Principal principal,
            @PathVariable String taskId
    ) {
        try {
            TaskListItemResponse deleted = taskService.deleteTask(principal.getName(), taskId);
            return ResponseEntity.ok(ApiResponse.success(deleted, "Xóa công việc thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PutMapping("/{taskId}/visibility")
    public ResponseEntity<ApiResponse<Void>> setVisibility(
            Principal principal,
            @PathVariable String taskId,
            @RequestBody TaskVisibilityRequest request
    ) {
        try {
            taskService.setVisibility(principal.getName(), taskId, request);
            return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật quyền công khai thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/shares")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> addShare(
            Principal principal,
            @PathVariable String taskId,
            @Valid @RequestBody TaskShareInputRequest request
    ) {
        try {
            TaskDetailResponse response = taskService.addShare(principal.getName(), taskId, request);
            return ResponseEntity.ok(ApiResponse.success(response, "Chia sẻ công việc thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @DeleteMapping("/{taskId}/shares/{userName}")
    public ResponseEntity<ApiResponse<Void>> removeShare(
            Principal principal,
            @PathVariable String taskId,
            @PathVariable String userName
    ) {
        try {
            taskService.removeShare(principal.getName(), taskId, userName);
            return ResponseEntity.ok(ApiResponse.success(null, "Xóa người được chia sẻ thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }
}
