package vn.com.d2s.smr.controller.sy;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.config.audit.Audited;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.sy.MinutesTemplateResponse;
import vn.com.d2s.smr.dto.sy.PublicSettingsResponse;
import vn.com.d2s.smr.dto.sy.SystemSettingsDto;
import vn.com.d2s.smr.service.sy.SettingService;

import java.security.Principal;
import java.util.List;

/**
 * Cấu hình hệ thống (P7). /api/admin/** chỉ Admin (SecurityConfig); /api/settings/public cho mọi người đã đăng nhập.
 * Không có bí mật (DB, MinIO, LLM, JWT) — các giá trị đó vẫn lấy từ biến môi trường.
 */
@RestController
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/api/admin/settings")
    public SystemSettingsDto getSettings() {
        return settingService.getSettings();
    }

    @Audited(category = "SETTINGS", action = "UPDATE", targetType = "SETTINGS")
    @PutMapping("/api/admin/settings")
    public SystemSettingsDto updateSettings(Principal principal, @Valid @RequestBody SystemSettingsDto request) {
        return settingService.updateSettings(request, principal.getName());
    }

    @GetMapping("/api/settings/public")
    public PublicSettingsResponse getPublicSettings() {
        return settingService.getPublicSettings();
    }

    @GetMapping("/api/admin/minutes-templates")
    public List<MinutesTemplateResponse> listTemplates() {
        return settingService.listTemplates();
    }

    @Audited(category = "SETTINGS", action = "UPLOAD_TEMPLATE", targetType = "MINUTES_TEMPLATE")
    @PostMapping(value = "/api/admin/minutes-templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MinutesTemplateResponse uploadTemplate(Principal principal, @RequestParam("file") MultipartFile file) {
        return settingService.uploadTemplate(file, principal.getName());
    }

    @Audited(category = "SETTINGS", action = "SET_DEFAULT_TEMPLATE", targetType = "MINUTES_TEMPLATE", targetPathVariable = "id")
    @PutMapping("/api/admin/minutes-templates/{id}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefaultTemplate(Principal principal, @PathVariable String id) {
        settingService.setDefaultTemplate(id, principal.getName());
    }

    @Audited(category = "SETTINGS", action = "DELETE_TEMPLATE", targetType = "MINUTES_TEMPLATE", targetPathVariable = "id")
    @DeleteMapping("/api/admin/minutes-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable String id) {
        settingService.deleteTemplate(id);
    }

    @GetMapping("/api/admin/minutes-templates/{id}/download")
    public FileDownloadResponse downloadTemplate(@PathVariable String id) {
        return settingService.getTemplateDownload(id);
    }
}
