package vn.com.d2s.smr.controller.mt;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.config.audit.Audited;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.dto.mt.minutes.MinutesDetailResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesJobResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesListItemResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesVersionResponse;
import vn.com.d2s.smr.dto.mt.minutes.RejectMinutesRequest;
import vn.com.d2s.smr.dto.mt.minutes.UpdateMinutesRequest;
import vn.com.d2s.smr.dto.mt.minutes.UpdateSegmentRequest;
import vn.com.d2s.smr.dto.mt.minutes.MinutesExportResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesTemplateInfo;
import vn.com.d2s.smr.service.mt.minutes.MinutesExportService;
import vn.com.d2s.smr.service.mt.minutes.MinutesService;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

/** P13 — biên bản họp: danh sách, trình soạn, sinh AI, quy trình duyệt, xuất DOCX, sửa transcript. */
@RestController
@RequestMapping("/api")
public class MinutesController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final MinutesService minutesService;
    private final MinutesExportService exportService;

    public MinutesController(MinutesService minutesService, MinutesExportService exportService) {
        this.minutesService = minutesService;
        this.exportService = exportService;
    }

    // ----- Mẫu biên bản của cuộc họp + "Tải biên bản họp" chạy ngầm (mọi thành viên)

    @PostMapping("/Meeting/{meetingId}/minutes/template")
    @Audited(category = "MINUTES", action = "UPLOAD_TEMPLATE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesTemplateInfo>> uploadTemplate(Principal principal, @PathVariable String meetingId,
                                                                          @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(exportService.uploadTemplate(principal.getName(), meetingId, file), "Đã tải mẫu biên bản lên."));
    }

    @GetMapping("/Meeting/{meetingId}/minutes/template")
    public ResponseEntity<ApiResponse<MinutesTemplateInfo>> template(Principal principal, @PathVariable String meetingId) {
        return ok(exportService.template(principal.getName(), meetingId));
    }

    @DeleteMapping("/Meeting/{meetingId}/minutes/template")
    @Audited(category = "MINUTES", action = "DELETE_TEMPLATE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(Principal principal, @PathVariable String meetingId) {
        exportService.deleteTemplate(principal.getName(), meetingId);
        return ok(null);
    }

    /** Trả về ngay; job chạy nền. FE theo dõi qua sự kiện MinutesExportReady hoặc GET trạng thái. */
    @PostMapping("/Meeting/{meetingId}/minutes/exports")
    @Audited(category = "MINUTES", action = "EXPORT", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesExportResponse>> requestExport(Principal principal, @PathVariable String meetingId) {
        return ok(exportService.requestExport(principal.getName(), meetingId));
    }

    @GetMapping("/minutes/exports/{exportId}")
    public ResponseEntity<ApiResponse<MinutesExportResponse>> exportStatus(Principal principal, @PathVariable String exportId) {
        return ok(exportService.status(principal.getName(), exportId));
    }

    @GetMapping("/minutes/exports/{exportId}/file")
    public ResponseEntity<byte[]> exportFile(Principal principal, @PathVariable String exportId) {
        MinutesService.ExportedDocx file = exportService.download(principal.getName(), exportId);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @GetMapping("/minutes")
    public ResponseEntity<ApiResponse<PagedResultResponse<MinutesListItemResponse>>> list(
            Principal principal,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ok(minutesService.list(principal.getName(), keyword, page, pageSize));
    }

    @GetMapping("/Meeting/{meetingId}/minutes")
    public ResponseEntity<ApiResponse<MinutesDetailResponse>> detail(Principal principal, @PathVariable String meetingId,
                                                                    @RequestParam(name = "version", required = false) Integer version) {
        return ok(minutesService.detail(principal.getName(), meetingId, version));
    }

    @PostMapping("/Meeting/{meetingId}/minutes/generate")
    @Audited(category = "MINUTES", action = "GENERATE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesJobResponse>> generate(Principal principal, @PathVariable String meetingId) {
        return ok(minutesService.generate(principal.getName(), meetingId));
    }

    @PostMapping("/Meeting/{meetingId}/minutes/drafts")
    @Audited(category = "MINUTES", action = "NEW_DRAFT", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesVersionResponse>> newDraft(Principal principal, @PathVariable String meetingId) {
        return ok(minutesService.newDraft(principal.getName(), meetingId));
    }

    @PutMapping("/Meeting/{meetingId}/minutes/{version}")
    @Audited(category = "MINUTES", action = "UPDATE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesVersionResponse>> update(Principal principal, @PathVariable String meetingId,
                                                                     @PathVariable int version, @RequestBody UpdateMinutesRequest request) {
        return ok(minutesService.update(principal.getName(), meetingId, version, request == null ? null : request.content()));
    }

    @PostMapping("/Meeting/{meetingId}/minutes/{version}/submit")
    @Audited(category = "MINUTES", action = "SUBMIT", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesVersionResponse>> submit(Principal principal, @PathVariable String meetingId, @PathVariable int version) {
        return ok(minutesService.submit(principal.getName(), meetingId, version));
    }

    @PostMapping("/Meeting/{meetingId}/minutes/{version}/approve")
    @Audited(category = "MINUTES", action = "APPROVE", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesVersionResponse>> approve(Principal principal, @PathVariable String meetingId, @PathVariable int version) {
        return ok(minutesService.approve(principal.getName(), meetingId, version));
    }

    @PostMapping("/Meeting/{meetingId}/minutes/{version}/reject")
    @Audited(category = "MINUTES", action = "REJECT", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesVersionResponse>> reject(Principal principal, @PathVariable String meetingId,
                                                                     @PathVariable int version, @RequestBody RejectMinutesRequest request) {
        return ok(minutesService.reject(principal.getName(), meetingId, version, request == null ? null : request.reason()));
    }

    @PostMapping("/Meeting/{meetingId}/minutes/{version}/publish")
    @Audited(category = "MINUTES", action = "PUBLISH", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MinutesVersionResponse>> publish(Principal principal, @PathVariable String meetingId, @PathVariable int version) {
        return ok(minutesService.publish(principal.getName(), meetingId, version));
    }

    @GetMapping("/Meeting/{meetingId}/minutes/{version}/docx")
    @Audited(category = "MINUTES", action = "EXPORT", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<byte[]> docx(Principal principal, @PathVariable String meetingId, @PathVariable int version) {
        MinutesService.ExportedDocx file = minutesService.exportDocx(principal.getName(), meetingId, version);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @PutMapping("/Meeting/{meetingId}/transcripts/{segmentId}")
    @Audited(category = "MINUTES", action = "EDIT_TRANSCRIPT", targetType = "MEETING", targetPathVariable = "meetingId")
    public ResponseEntity<ApiResponse<MeetingCaptionDto>> updateSegment(Principal principal, @PathVariable String meetingId,
                                                                       @PathVariable String segmentId, @RequestBody UpdateSegmentRequest request) {
        return ok(minutesService.updateSegment(principal.getName(), meetingId, segmentId,
                request == null ? null : request.text(), request == null ? null : request.speakerName()));
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
        HttpStatus status = e.getMessage() != null && (e.getMessage().startsWith("Không tìm thấy") || e.getMessage().contains("không tồn tại"))
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.failure(e.getMessage()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data, null));
    }
}
