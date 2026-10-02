package vn.com.d2s.smr.dto.common;

import java.util.List;

public record PagedResultResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
    public static <T> PagedResultResponse<T> of(List<T> items, int page, int pageSize, long totalItems) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalItems / pageSize) : 0;
        return new PagedResultResponse<>(items, page, pageSize, totalItems, totalPages);
    }
}
