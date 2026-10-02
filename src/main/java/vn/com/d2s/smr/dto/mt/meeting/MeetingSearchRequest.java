package vn.com.d2s.smr.dto.mt.meeting;

import java.time.LocalDateTime;

public record MeetingSearchRequest(
        String tab,
        String keyword,
        Integer status,
        String organizationId,
        int page,
        int pageSize,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    public MeetingSearchRequest {
        tab = (tab == null || tab.isBlank()) ? "all" : tab.trim();
        keyword = (keyword == null) ? "" : keyword.trim();
        page = Math.max(1, page == 0 ? 1 : page);
        pageSize = Math.min(100, Math.max(1, pageSize == 0 ? 10 : pageSize));
    }
}
