package vn.com.d2s.smr.controller.mt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.config.audit.Audited;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.mt.poll.CreatePollRequest;
import vn.com.d2s.smr.dto.mt.poll.PollResponse;
import vn.com.d2s.smr.service.mt.PollService;

import java.security.Principal;
import java.util.List;

/** Biểu quyết trong phòng họp: /api/Meeting/{meetingId}/polls. */
@RestController
@RequestMapping("/api/Meeting/{meetingId}/polls")
public class PollController {

    /** Bỏ phiếu: chỉ số phương án. */
    public record VoteRequest(int optionIndex) {
    }

    /** Danh sách biểu quyết + quyền tạo của người xem. */
    public record PollListResponse(boolean canCreate, List<PollResponse> polls) {
    }

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PollListResponse>> list(Principal principal, @PathVariable String meetingId) {
        return ok(new PollListResponse(pollService.canCreate(principal.getName(), meetingId), pollService.list(principal.getName(), meetingId)));
    }

    @PostMapping
    @Audited(category = "MEETING", action = "POLL_CREATE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<PollResponse>> create(Principal principal, @PathVariable String meetingId,
                                                            @RequestBody CreatePollRequest request) {
        return ok(pollService.create(principal.getName(), meetingId, request));
    }

    @PostMapping("/{pollId}/vote")
    public ResponseEntity<ApiResponse<PollResponse>> vote(Principal principal, @PathVariable String meetingId,
                                                          @PathVariable String pollId, @RequestBody VoteRequest request) {
        return ok(pollService.vote(principal.getName(), meetingId, pollId, request.optionIndex()));
    }

    @PostMapping("/{pollId}/close")
    @Audited(category = "MEETING", action = "POLL_CLOSE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<PollResponse>> close(Principal principal, @PathVariable String meetingId, @PathVariable String pollId) {
        return ok(pollService.close(principal.getName(), meetingId, pollId));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> state(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException e) {
        HttpStatus status = e.getMessage() != null && e.getMessage().contains("không tồn tại") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.failure(e.getMessage()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data, null));
    }
}
