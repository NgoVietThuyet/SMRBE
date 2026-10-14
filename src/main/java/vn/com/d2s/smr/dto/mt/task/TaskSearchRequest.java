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
        int pageSize,
        /** P12: mine (tôi tạo) · assigned (giao cho tôi) · shared (được chia sẻ) · public · all. */
        String group,
        /** P12: mặc định ẩn việc đã lưu trữ. */
        Boolean includeArchived
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

    public TaskSearchRequest(String shortcut, String keyword, String meetingId, String assignee, Integer status,
                             Integer priority, Integer level, String parentId, int page, int pageSize) {
        this(shortcut, keyword, meetingId, assignee, status, priority, level, parentId, page, pageSize, null, null);
    }
}
