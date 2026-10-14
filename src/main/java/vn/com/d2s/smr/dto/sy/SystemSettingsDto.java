package vn.com.d2s.smr.dto.sy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Cấu hình hệ thống không bí mật (GET/PUT /api/admin/settings). */
public record SystemSettingsDto(
        @Min(value = 1, message = "Dung lượng tối đa phải từ 1 MB.")
        @Max(value = 2048, message = "Dung lượng tối đa không vượt quá 2048 MB.")
        int maxUploadSizeMb,
        @NotEmpty(message = "Phải chọn ít nhất một loại tệp.")
        List<String> allowedExtensions,
        @Min(value = 1, message = "Thời gian lưu bản ghi tối thiểu 1 ngày.")
        @Max(value = 3650, message = "Thời gian lưu bản ghi tối đa 3650 ngày.")
        int recordingRetentionDays,
        String defaultMinutesTemplateId
) {
}
