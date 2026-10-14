package vn.com.d2s.smr.common;

import vn.com.d2s.smr.dto.sy.PublicSettingsResponse;
import vn.com.d2s.smr.service.sy.SettingService;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Giới hạn tải tài liệu theo Cấu hình hệ thống (P7: dung lượng tối đa, đuôi file cho phép).
 * Dùng chung cho tài liệu cuộc họp và đính kèm công việc. Cấu hình lỗi / chưa có → dùng giá trị mặc định.
 * Các đuôi có thể chạy mã (html, svg, js, exe...) luôn bị chặn dù cấu hình có cho phép.
 */
public record UploadLimits(int maxMb, Set<String> allowedExtensions) {

    public static final int DEFAULT_MAX_MB = 200;
    public static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".png", ".jpg", ".jpeg", ".zip");
    private static final Set<String> ALWAYS_BLOCKED = Set.of(
            ".html", ".htm", ".svg", ".js", ".mjs", ".exe", ".dll", ".bat", ".cmd", ".ps1", ".sh", ".php", ".jsp", ".aspx", ".msi", ".jar");

    public static UploadLimits from(SettingService settings) {
        int maxMb = DEFAULT_MAX_MB;
        Set<String> allowed = DEFAULT_EXTENSIONS;
        if (settings != null) {
            try {
                PublicSettingsResponse s = settings.getPublicSettings();
                if (s.maxUploadSizeMb() > 0) maxMb = s.maxUploadSizeMb();
                if (s.allowedExtensions() != null && !s.allowedExtensions().isEmpty()) {
                    allowed = s.allowedExtensions().stream()
                            .map(e -> (e.startsWith(".") ? e : "." + e).toLowerCase(Locale.ROOT))
                            .collect(Collectors.toUnmodifiableSet());
                }
            } catch (RuntimeException ignored) {
                // dùng giới hạn mặc định
            }
        }
        return new UploadLimits(maxMb, allowed);
    }

    public long maxBytes() {
        return maxMb * 1024L * 1024L;
    }

    /** Ném IllegalArgumentException với thông báo tiếng Việt nếu file vượt giới hạn hoặc sai định dạng. */
    public void check(String fileName, long size) {
        if (size > maxBytes()) {
            throw new IllegalArgumentException("File vượt quá " + maxMb + "MB.");
        }
        String name = fileName == null ? "" : fileName;
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (ext.isEmpty() || ALWAYS_BLOCKED.contains(ext) || !allowedExtensions.contains(ext)) {
            throw new IllegalArgumentException("Định dạng " + (ext.isEmpty() ? "(không có)" : ext) + " không được hỗ trợ.");
        }
    }
}
