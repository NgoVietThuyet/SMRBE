package vn.com.d2s.smr.service.sy;

import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.sy.MinutesTemplateResponse;
import vn.com.d2s.smr.dto.sy.PublicSettingsResponse;
import vn.com.d2s.smr.dto.sy.SystemSettingsDto;

import java.util.List;

public interface SettingService {

    SystemSettingsDto getSettings();

    SystemSettingsDto updateSettings(SystemSettingsDto request, String actor);

    PublicSettingsResponse getPublicSettings();

    List<MinutesTemplateResponse> listTemplates();

    MinutesTemplateResponse uploadTemplate(MultipartFile file, String actor);

    void setDefaultTemplate(String id, String actor);

    void deleteTemplate(String id);

    FileDownloadResponse getTemplateDownload(String id);

    /** P13: objectName của mẫu biên bản — ưu tiên mẫu chọn riêng cho cuộc họp, rồi mẫu mặc định; rỗng nếu chưa có mẫu. */
    java.util.Optional<String> templateObjectName(String preferredTemplateId);
}
