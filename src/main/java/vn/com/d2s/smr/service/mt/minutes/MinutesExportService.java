package vn.com.d2s.smr.service.mt.minutes;

import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.mt.minutes.MinutesExportResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesTemplateInfo;

/**
 * P13 bổ sung — mẫu biên bản do thành viên tải lên + "Tải biên bản họp" chạy ngầm.
 * <ul>
 *   <li>Tải mẫu lên chỉ lưu mẫu (mỗi cuộc họp một mẫu: mẫu sau thay mẫu trước), không chạy AI.</li>
 *   <li>Yêu cầu tải biên bản tạo job nền, trả về ngay; job xong thì FE tự tải file và báo thành công.</li>
 * </ul>
 * Mọi thành viên cuộc họp (trừ khách) đều dùng được.
 * Lỗi: IllegalArgumentException → 400/404 · SecurityException → 403 · IllegalStateException → 409.
 */
public interface MinutesExportService {

    MinutesTemplateInfo uploadTemplate(String userName, String meetingId, MultipartFile file);

    /** Mẫu đang dùng: mẫu của cuộc họp → mẫu mặc định hệ thống → bố cục dựng sẵn. */
    MinutesTemplateInfo template(String userName, String meetingId);

    /** Người tải mẫu hoặc chủ trì. */
    void deleteTemplate(String userName, String meetingId);

    MinutesExportResponse requestExport(String userName, String meetingId);

    MinutesExportResponse status(String userName, String exportId);

    MinutesService.ExportedDocx download(String userName, String exportId);
}
