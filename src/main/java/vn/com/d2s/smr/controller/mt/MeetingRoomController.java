package vn.com.d2s.smr.controller.mt;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.config.audit.Audited;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.mt.room.AttendanceSummaryResponse;
import vn.com.d2s.smr.dto.mt.room.ChangeRoleRequest;
import vn.com.d2s.smr.dto.mt.room.EnterRoomResponse;
import vn.com.d2s.smr.dto.mt.room.LobbyRequestResponse;
import vn.com.d2s.smr.dto.mt.room.PublicMeetingResponse;
import vn.com.d2s.smr.dto.mt.room.RoomParticipantResponse;
import vn.com.d2s.smr.dto.mt.room.SaveWhiteboardRequest;
import vn.com.d2s.smr.dto.mt.room.ToggleRequest;
import vn.com.d2s.smr.dto.mt.room.WhiteboardResponse;
import vn.com.d2s.smr.service.mt.MeetingRoomService;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

/**
 * P10 — Phòng họp: /api/Meeting/{meetingId}/room/** (+ tham dự, + thông tin công khai cho khách).
 * Lỗi nghiệp vụ được ánh xạ ở các @ExceptionHandler cuối file.
 */
@RestController
@RequestMapping("/api/Meeting")
public class MeetingRoomController {

    private final MeetingRoomService roomService;

    public MeetingRoomController(MeetingRoomService roomService) {
        this.roomService = roomService;
    }

    // ----- Vào phòng / phòng chờ

    @PostMapping("/{meetingId}/room/enter")
    public ResponseEntity<ApiResponse<EnterRoomResponse>> enter(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.enter(principal.getName(), meetingId));
    }

    @PostMapping("/{meetingId}/room/lobby/request")
    public ResponseEntity<ApiResponse<EnterRoomResponse>> requestAgain(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.requestAgain(principal.getName(), meetingId));
    }

    @GetMapping("/{meetingId}/room/lobby")
    public ResponseEntity<ApiResponse<List<LobbyRequestResponse>>> lobby(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.lobby(principal.getName(), meetingId));
    }

    @PostMapping("/{meetingId}/room/lobby/{requestId}/admit")
    public ResponseEntity<ApiResponse<Void>> admit(Principal principal, @PathVariable String meetingId, @PathVariable String requestId) {
        roomService.decide(principal.getName(), meetingId, requestId, true);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/{meetingId}/room/lobby/{requestId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(Principal principal, @PathVariable String meetingId, @PathVariable String requestId) {
        roomService.decide(principal.getName(), meetingId, requestId, false);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/{meetingId}/room/lobby/admit-all")
    public ResponseEntity<ApiResponse<Integer>> admitAll(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.admitAll(principal.getName(), meetingId));
    }

    // ----- Thành viên

    @GetMapping("/{meetingId}/room/participants")
    public ResponseEntity<ApiResponse<List<RoomParticipantResponse>>> participants(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.participants(principal.getName(), meetingId));
    }

    @PostMapping("/{meetingId}/room/participants/{userName}/mute")
    public ResponseEntity<ApiResponse<Void>> mute(Principal principal, @PathVariable String meetingId, @PathVariable String userName) {
        roomService.mute(principal.getName(), meetingId, userName);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @Audited(category = "MEETING", action = "KICK", targetType = "MEETING", targetPathVariable = "meetingId")
    @PostMapping("/{meetingId}/room/participants/{userName}/kick")
    public ResponseEntity<ApiResponse<Void>> kick(Principal principal, @PathVariable String meetingId, @PathVariable String userName) {
        roomService.kick(principal.getName(), meetingId, userName);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã mời ra khỏi phòng."));
    }

    @Audited(category = "MEETING", action = "CHANGE_ROLE", targetType = "MEETING", targetPathVariable = "meetingId")
    @PutMapping("/{meetingId}/room/participants/{userName}/role")
    public ResponseEntity<ApiResponse<Void>> changeRole(Principal principal, @PathVariable String meetingId,
                                                        @PathVariable String userName, @RequestBody ChangeRoleRequest request) {
        roomService.changeRole(principal.getName(), meetingId, userName, request == null ? 0 : request.role());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã đổi vai trò."));
    }

    // ----- Bảng trắng

    @GetMapping("/{meetingId}/room/whiteboard")
    public ResponseEntity<ApiResponse<WhiteboardResponse>> whiteboard(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.whiteboard(principal.getName(), meetingId));
    }

    @PutMapping("/{meetingId}/room/whiteboard")
    public ResponseEntity<ApiResponse<WhiteboardResponse>> saveWhiteboard(Principal principal, @PathVariable String meetingId,
                                                                          @RequestBody SaveWhiteboardRequest request) {
        return ok(roomService.saveWhiteboard(principal.getName(), meetingId, request));
    }

    @PostMapping("/{meetingId}/room/whiteboard/lock")
    public ResponseEntity<ApiResponse<WhiteboardResponse>> lockWhiteboard(Principal principal, @PathVariable String meetingId,
                                                                          @RequestBody ToggleRequest request) {
        return ok(roomService.lockWhiteboard(principal.getName(), meetingId, request != null && request.on()));
    }

    // ----- Ghi hình

    @Audited(category = "MEETING", action = "RECORDING", targetType = "MEETING", targetPathVariable = "meetingId")
    @PostMapping("/{meetingId}/room/recording")
    public ResponseEntity<ApiResponse<Void>> recording(Principal principal, @PathVariable String meetingId, @RequestBody ToggleRequest request) {
        roomService.recording(principal.getName(), meetingId, request != null && request.on());
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    // ----- Tham dự

    @GetMapping("/{meetingId}/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceSummaryResponse>>> attendance(Principal principal, @PathVariable String meetingId) {
        return ok(roomService.attendance(principal.getName(), meetingId));
    }

    @GetMapping("/{meetingId}/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(Principal principal, @PathVariable String meetingId) {
        MeetingRoomService.ExportedFile file = roomService.exportAttendance(principal.getName(), meetingId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    // ----- Màn /join/:roomCode (không cần đăng nhập — mở trong SecurityConfig)

    @GetMapping("/public/{roomCode}")
    public ResponseEntity<ApiResponse<PublicMeetingResponse>> publicInfo(@PathVariable String roomCode) {
        return ok(roomService.publicInfo(roomCode));
    }

    // ----- Ánh xạ lỗi

    @ExceptionHandler(MeetingRoomService.WhiteboardConflictException.class)
    public ResponseEntity<ApiResponse<WhiteboardResponse>> conflict(MeetingRoomService.WhiteboardConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(false, e.getMessage(), e.current()));
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
