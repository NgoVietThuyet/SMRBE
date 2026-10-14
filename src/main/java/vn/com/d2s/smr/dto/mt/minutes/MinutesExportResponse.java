package vn.com.d2s.smr.dto.mt.minutes;

/** Job tổng hợp biên bản để tải về. status: 0 chờ · 1 đang chạy · 2 xong · 3 lỗi. reused = dùng lại job / file đã có. */
public record MinutesExportResponse(
        String id,
        String meetingId,
        String meetingName,
        int status,
        String error,
        boolean reused
) {
}
