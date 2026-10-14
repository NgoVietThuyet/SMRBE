package vn.com.d2s.smr.dto.sy;

import java.util.List;

/** Phần cấu hình mọi người dùng đã đăng nhập được đọc (giới hạn upload) — GET /api/settings/public. */
public record PublicSettingsResponse(
        int maxUploadSizeMb,
        List<String> allowedExtensions
) {
}
