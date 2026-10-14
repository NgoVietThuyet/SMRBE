package vn.com.d2s.smr.dto.mt.minutes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mẫu biên bản đang dùng của cuộc họp. source: MEETING (mẫu thành viên tải lên) · SYSTEM (mẫu mặc định P7) · BUILTIN (bố cục dựng sẵn).
 * sections: tên các mục hệ thống nhận ra trong mẫu (rỗng với mẫu dùng placeholder).
 */
public record MinutesTemplateInfo(
        String source,
        String fileId,
        String fileName,
        String format,
        String uploadedBy,
        String uploadedByName,
        LocalDateTime uploadedAt,
        List<String> sections,
        boolean canDelete
) {
}
