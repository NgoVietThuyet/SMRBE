package vn.com.d2s.smr.controller.sy;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.sy.AuditLogResponse;
import vn.com.d2s.smr.service.sy.AuditService;

import java.time.LocalDateTime;

/** Tra cứu nhật ký hoạt động — chỉ Admin (SecurityConfig: /api/admin/**). Thời gian tính theo UTC như khi ghi. */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PagedResultResponse<AuditLogResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return auditService.search(from, to, category, actor, success, keyword, page, pageSize);
    }
}
