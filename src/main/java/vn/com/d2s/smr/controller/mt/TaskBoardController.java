package vn.com.d2s.smr.controller.mt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.mt.task.board.BoardResponse;
import vn.com.d2s.smr.dto.mt.task.board.CardDetailResponse;
import vn.com.d2s.smr.dto.mt.task.board.CardLabelsRequest;
import vn.com.d2s.smr.dto.mt.task.board.ChecklistItemRequest;
import vn.com.d2s.smr.dto.mt.task.board.ChecklistItemResponse;
import vn.com.d2s.smr.dto.mt.task.board.CommentRequest;
import vn.com.d2s.smr.dto.mt.task.board.CommentResponse;
import vn.com.d2s.smr.dto.mt.task.board.LabelRequest;
import vn.com.d2s.smr.dto.mt.task.board.LabelResponse;
import vn.com.d2s.smr.dto.mt.task.board.MoveTaskRequest;
import vn.com.d2s.smr.dto.mt.task.board.TaskFileResponse;
import vn.com.d2s.smr.dto.mt.task.board.TaskGroupsResponse;
import vn.com.d2s.smr.service.mt.TaskBoardService;

import java.security.Principal;
import java.util.List;

/** P12 — bảng công việc: /api/Task/groups, /{workId}/board, kéo thả, nhãn, checklist, bình luận, đính kèm. */
@RestController
@RequestMapping("/api/Task")
public class TaskBoardController {

    private final TaskBoardService boardService;

    public TaskBoardController(TaskBoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<TaskGroupsResponse>> groups(Principal principal) {
        return ok(boardService.groups(principal.getName()));
    }

    @GetMapping("/{workId}/board")
    public ResponseEntity<ApiResponse<BoardResponse>> board(Principal principal, @PathVariable String workId,
                                                           @RequestParam(name = "archived", defaultValue = "false") boolean archived) {
        return ok(boardService.board(principal.getName(), workId, archived));
    }

    @PostMapping("/{taskId}/move")
    public ResponseEntity<ApiResponse<Void>> move(Principal principal, @PathVariable String taskId, @RequestBody MoveTaskRequest request) {
        boardService.move(principal.getName(), taskId, request);
        return ok(null);
    }

    @GetMapping("/{taskId}/card")
    public ResponseEntity<ApiResponse<CardDetailResponse>> card(Principal principal, @PathVariable String taskId) {
        return ok(boardService.card(principal.getName(), taskId));
    }

    // ----- Nhãn
    @PostMapping("/{workId}/labels")
    public ResponseEntity<ApiResponse<LabelResponse>> createLabel(Principal principal, @PathVariable String workId, @RequestBody LabelRequest request) {
        return ok(boardService.createLabel(principal.getName(), workId, request));
    }

    @PutMapping("/labels/{labelId}")
    public ResponseEntity<ApiResponse<LabelResponse>> updateLabel(Principal principal, @PathVariable String labelId, @RequestBody LabelRequest request) {
        return ok(boardService.updateLabel(principal.getName(), labelId, request));
    }

    @DeleteMapping("/labels/{labelId}")
    public ResponseEntity<ApiResponse<Void>> deleteLabel(Principal principal, @PathVariable String labelId) {
        boardService.deleteLabel(principal.getName(), labelId);
        return ok(null);
    }

    @PutMapping("/{taskId}/labels")
    public ResponseEntity<ApiResponse<List<String>>> setCardLabels(Principal principal, @PathVariable String taskId, @RequestBody CardLabelsRequest request) {
        return ok(boardService.setCardLabels(principal.getName(), taskId, request));
    }

    // ----- Checklist
    @PostMapping("/{taskId}/checklist")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addChecklist(Principal principal, @PathVariable String taskId, @RequestBody ChecklistItemRequest request) {
        return ok(boardService.addChecklistItem(principal.getName(), taskId, request));
    }

    @PatchMapping("/checklist/{itemId}")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> updateChecklist(Principal principal, @PathVariable String itemId, @RequestBody ChecklistItemRequest request) {
        return ok(boardService.updateChecklistItem(principal.getName(), itemId, request));
    }

    @DeleteMapping("/checklist/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteChecklist(Principal principal, @PathVariable String itemId) {
        boardService.deleteChecklistItem(principal.getName(), itemId);
        return ok(null);
    }

    // ----- Bình luận
    @PostMapping("/{taskId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(Principal principal, @PathVariable String taskId, @RequestBody CommentRequest request) {
        return ok(boardService.addComment(principal.getName(), taskId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(Principal principal, @PathVariable String commentId) {
        boardService.deleteComment(principal.getName(), commentId);
        return ok(null);
    }

    // ----- Đính kèm
    @PostMapping("/{taskId}/files")
    public ResponseEntity<ApiResponse<TaskFileResponse>> upload(Principal principal, @PathVariable String taskId, @RequestParam("file") MultipartFile file) {
        return ok(boardService.uploadFile(principal.getName(), taskId, file));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(Principal principal, @PathVariable String fileId) {
        boardService.deleteFile(principal.getName(), fileId);
        return ok(null);
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<ApiResponse<FileDownloadResponse>> download(Principal principal, @PathVariable String fileId) {
        return ok(boardService.downloadUrl(principal.getName(), fileId));
    }

    // ----- Ánh xạ lỗi
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Không tìm thấy") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.failure(e.getMessage()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data, null));
    }
}
