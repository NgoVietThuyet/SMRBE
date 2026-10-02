package vn.com.d2s.smr.dto.mt.task;

public record TaskSearchRequest(
        String shortcut,
        String keyword,
        String meetingId,
        String assignee,
        Integer status,
        Integer priority,
        Integer level,
        String parentId,
        int page,
        int pageSize
) {
    public TaskSearchRequest {
        if (shortcut == null || shortcut.isBlank()) {
            shortcut = "all";
        }
        if (keyword == null) {
            keyword = "";
        }
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 50;
        }
    }
}
