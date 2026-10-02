package vn.com.d2s.smr.controller.mt;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.CancelMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.CreateMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDashboardResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDetailResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingJoinInfoResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingListItemResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSearchRequest;
import vn.com.d2s.smr.dto.mt.meeting.QuickMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingParticipantsRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingRequest;
import vn.com.d2s.smr.dto.mt.message.MeetingMessageResponse;
import vn.com.d2s.smr.dto.mt.message.SendMeetingMessageRequest;
import vn.com.d2s.smr.service.mt.MeetingService;

import org.springframework.beans.factory.annotation.Autowired;
import vn.com.d2s.smr.dto.mt.ai.MeetingAiChatRequest;
import vn.com.d2s.smr.dto.mt.ai.MeetingAiChatResponse;
import vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.service.mt.MeetingRagService;
import vn.com.d2s.smr.service.mt.MeetingTranscriptService;
import vn.com.d2s.smr.service.mt.impl.MeetingTranscriptServiceImpl;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/Meeting")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingTranscriptService transcriptService;
    private final MeetingRagService ragService;

    @Autowired
    public MeetingController(MeetingService meetingService, MeetingTranscriptService transcriptService, MeetingRagService ragService) {
        this.meetingService = meetingService;
        this.transcriptService = transcriptService;
        this.ragService = ragService;
    }

    public MeetingController(MeetingService meetingService, MeetingTranscriptService transcriptService) {
        this(meetingService, transcriptService, null);
    }

    public MeetingController(MeetingService meetingService) {
        this(meetingService, new MeetingTranscriptServiceImpl(), null);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<MeetingDashboardResponse>> getDashboard(Principal principal) {
        MeetingDashboardResponse dashboard = meetingService.getDashboard(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(dashboard, null));
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<PagedResultResponse<MeetingListItemResponse>>> searchMeetings(
            Principal principal,
            @RequestBody MeetingSearchRequest request
    ) {
        PagedResultResponse<MeetingListItemResponse> result = meetingService.searchMeetings(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(result, null));
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingDetailResponse>> getMeetingDetail(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            return meetingService.getMeetingDetail(principal.getName(), meetingId)
                    .map(detail -> ResponseEntity.ok(ApiResponse.success(detail, null)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.failure("Không tìm thấy cuộc họp.")));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MeetingDetailResponse>> createMeeting(
            Principal principal,
            @Valid @RequestBody CreateMeetingRequest request
    ) {
        try {
            MeetingDetailResponse created = meetingService.createMeeting(principal.getName(), request);
            return ResponseEntity.ok(ApiResponse.success(created, "Tạo cuộc họp thành công."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PatchMapping("/{meetingId}")
    public ResponseEntity<ApiResponse<MeetingDetailResponse>> updateMeeting(
            Principal principal,
            @PathVariable String meetingId,
            @Valid @RequestBody UpdateMeetingRequest request
    ) {
        try {
            MeetingDetailResponse updated = meetingService.updateMeeting(principal.getName(), meetingId, request);
            return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @DeleteMapping("/{meetingId}")
    public ResponseEntity<ApiResponse<Void>> deleteMeeting(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.deleteMeeting(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(null, "Xóa cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMeeting(
            Principal principal,
            @PathVariable String meetingId,
            @Valid @RequestBody CancelMeetingRequest request
    ) {
        try {
            meetingService.cancelMeeting(principal.getName(), meetingId, request);
            return ResponseEntity.ok(ApiResponse.success(null, "Hủy cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/archive")
    public ResponseEntity<ApiResponse<Void>> archiveMeeting(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.archiveMeeting(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(null, "Lưu trữ cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/quick")
    public ResponseEntity<ApiResponse<MeetingDetailResponse>> createQuickMeeting(
            Principal principal,
            @Valid @RequestBody QuickMeetingRequest request
    ) {
        try {
            MeetingDetailResponse created = meetingService.createQuickMeeting(principal.getName(), request);
            return ResponseEntity.ok(ApiResponse.success(created, "Tạo cuộc họp nhanh thành công."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/participants")
    public ResponseEntity<ApiResponse<MeetingDetailResponse>> addParticipants(
            Principal principal,
            @PathVariable String meetingId,
            @RequestBody UpdateMeetingParticipantsRequest request
    ) {
        try {
            MeetingDetailResponse updated = meetingService.addParticipants(principal.getName(), meetingId, request);
            return ResponseEntity.ok(ApiResponse.success(updated, "Thêm thành viên thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @DeleteMapping("/{meetingId}/participants/{participantUserName}")
    public ResponseEntity<ApiResponse<Void>> removeParticipant(
            Principal principal,
            @PathVariable String meetingId,
            @PathVariable String participantUserName
    ) {
        try {
            meetingService.removeParticipant(principal.getName(), meetingId, participantUserName);
            return ResponseEntity.ok(ApiResponse.success(null, "Xóa thành viên thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @GetMapping("/{meetingId}/join-info")
    public ResponseEntity<ApiResponse<MeetingJoinInfoResponse>> getJoinInfo(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            MeetingJoinInfoResponse joinInfo = meetingService.getJoinInfo(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(joinInfo, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/start")
    public ResponseEntity<ApiResponse<Void>> startMeeting(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.startMeeting(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(null, "Bắt đầu cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/end")
    public ResponseEntity<ApiResponse<Void>> endMeeting(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.endMeeting(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(null, "Kết thúc cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/join")
    public ResponseEntity<ApiResponse<Void>> joinMeeting(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.joinMeeting(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(null, "Tham gia cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveMeeting(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.leaveMeeting(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(null, "Rời cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @GetMapping("/{meetingId}/messages")
    public ResponseEntity<ApiResponse<List<MeetingMessageResponse>>> getMessages(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            List<MeetingMessageResponse> messages = meetingService.getMessages(principal.getName(), meetingId);
            return ResponseEntity.ok(ApiResponse.success(messages, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/messages")
    public ResponseEntity<ApiResponse<MeetingMessageResponse>> sendMessage(
            Principal principal,
            @PathVariable String meetingId,
            @Valid @RequestBody SendMeetingMessageRequest request
    ) {
        try {
            MeetingMessageResponse response = meetingService.sendMessage(principal.getName(), meetingId, request);
            return ResponseEntity.ok(ApiResponse.success(response, "Gửi tin nhắn thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @GetMapping("/{meetingId}/transcripts")
    public ResponseEntity<ApiResponse<List<MeetingCaptionDto>>> getTranscripts(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.getMeetingDetail(principal.getName(), meetingId);
            List<MeetingCaptionDto> list = transcriptService.getTranscriptsByMeetingId(meetingId);
            return ResponseEntity.ok(ApiResponse.success(list, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/transcripts")
    public ResponseEntity<ApiResponse<MeetingCaptionDto>> saveTranscript(
            Principal principal,
            @PathVariable String meetingId,
            @RequestBody MeetingCaptionDto request
    ) {
        try {
            meetingService.getMeetingDetail(principal.getName(), meetingId);
            MeetingCaptionDto saved = transcriptService.saveTranscriptSegment(meetingId, request);
            return ResponseEntity.ok(ApiResponse.success(saved, "Lưu phụ đề thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @GetMapping("/{meetingId}/ai/status")
    public ResponseEntity<ApiResponse<MeetingRagStatusResponse>> getAiRagStatus(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.getMeetingDetail(principal.getName(), meetingId);
            if (ragService == null) {
                return ResponseEntity.ok(ApiResponse.success(new MeetingRagStatusResponse(meetingId, false, 0, null, "Dịch vụ RAG chưa sẵn sàng"), null));
            }
            MeetingRagStatusResponse status = ragService.getRagStatus(meetingId);
            return ResponseEntity.ok(ApiResponse.success(status, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/ai/sync")
    public ResponseEntity<ApiResponse<MeetingRagStatusResponse>> syncAiRag(
            Principal principal,
            @PathVariable String meetingId
    ) {
        try {
            meetingService.getMeetingDetail(principal.getName(), meetingId);
            if (ragService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.failure("Dịch vụ RAG chưa sẵn sàng."));
            }
            MeetingRagStatusResponse status = ragService.syncKnowledgeBase(meetingId, principal.getName());
            return ResponseEntity.ok(ApiResponse.success(status, "Đồng bộ cơ sở tri thức cuộc họp thành công."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/{meetingId}/ai/chat")
    public ResponseEntity<ApiResponse<MeetingAiChatResponse>> askMeetingAi(
            Principal principal,
            @PathVariable String meetingId,
            @Valid @RequestBody MeetingAiChatRequest request
    ) {
        try {
            meetingService.getMeetingDetail(principal.getName(), meetingId);
            if (ragService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.failure("Dịch vụ RAG chưa sẵn sàng."));
            }
            MeetingAiChatResponse response = ragService.askMeetingAssistant(meetingId, principal.getName(), request.query());
            return ResponseEntity.ok(ApiResponse.success(response, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }
}
