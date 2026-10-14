package vn.com.d2s.smr.service.sy;

import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.sy.AuditEntry;
import vn.com.d2s.smr.dto.sy.AuditLogResponse;

import java.time.LocalDateTime;

public interface AuditService {

    /** Ghi một dòng nhật ký. Không bao giờ ném lỗi ra ngoài (lỗi ghi log không được làm hỏng nghiệp vụ). */
    void log(AuditEntry entry);

    PagedResultResponse<AuditLogResponse> search(
            LocalDateTime from, LocalDateTime to, String category, String actor, Boolean success,
            String keyword, int page, int pageSize
    );
}
