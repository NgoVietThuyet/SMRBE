package vn.com.d2s.smr.service.sy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.sy.MinutesTemplateResponse;
import vn.com.d2s.smr.dto.sy.PublicSettingsResponse;
import vn.com.d2s.smr.dto.sy.SystemSettingsDto;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.sy.SySetting;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.sy.SySettingRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.sy.SettingService;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SettingServiceImpl implements SettingService {

    /** CmFiles.Type của mẫu biên bản DOCX (SMR-DB §6). */
    public static final int TEMPLATE_FILE_TYPE = 5;
    static final String TEMPLATE_REFERENCE = "MINUTES_TEMPLATE";

    static final String KEY_MAX_UPLOAD_MB = "file.maxUploadSizeMb";
    static final String KEY_ALLOWED_EXTENSIONS = "file.allowedExtensions";
    static final String KEY_RECORDING_RETENTION_DAYS = "recording.retentionDays";
    static final String KEY_DEFAULT_TEMPLATE = "minutes.defaultTemplateId";

    static final int DEFAULT_MAX_UPLOAD_MB = 200;
    static final List<String> DEFAULT_EXTENSIONS = List.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".png", ".jpg", ".jpeg", ".zip"
    );
    static final int DEFAULT_RECORDING_RETENTION_DAYS = 365;

    private static final long MAX_TEMPLATE_BYTES = 10L * 1024 * 1024;
    private static final Pattern EXTENSION = Pattern.compile("^\\.[a-z0-9]{1,10}$");
    private static final List<String> BLOCKED_EXTENSIONS = List.of(
            ".exe", ".dll", ".bat", ".cmd", ".ps1", ".sh", ".js", ".html", ".htm", ".svg", ".php", ".jsp", ".aspx"
    );

    private final SySettingRepository settingRepository;
    private final CmFileRepository cmFileRepository;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SettingServiceImpl(
            SySettingRepository settingRepository,
            CmFileRepository cmFileRepository,
            ObjectStorageService storageService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.settingRepository = settingRepository;
        this.cmFileRepository = cmFileRepository;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettingsDto getSettings() {
        return new SystemSettingsDto(
                read(KEY_MAX_UPLOAD_MB, Integer.class, DEFAULT_MAX_UPLOAD_MB),
                read(KEY_ALLOWED_EXTENSIONS, new TypeReference<List<String>>() { }, DEFAULT_EXTENSIONS),
                read(KEY_RECORDING_RETENTION_DAYS, Integer.class, DEFAULT_RECORDING_RETENTION_DAYS),
                read(KEY_DEFAULT_TEMPLATE, String.class, "")
        );
    }

    @Override
    @Transactional
    public SystemSettingsDto updateSettings(SystemSettingsDto request, String actor) {
        List<String> extensions = normalizeExtensions(request.allowedExtensions());
        String templateId = request.defaultMinutesTemplateId() == null ? "" : request.defaultMinutesTemplateId().trim();
        if (!templateId.isEmpty() && !isTemplate(templateId)) {
            throw new IllegalArgumentException("Mẫu biên bản mặc định không tồn tại.");
        }

        write(KEY_MAX_UPLOAD_MB, request.maxUploadSizeMb(), actor);
        write(KEY_ALLOWED_EXTENSIONS, extensions, actor);
        write(KEY_RECORDING_RETENTION_DAYS, request.recordingRetentionDays(), actor);
        write(KEY_DEFAULT_TEMPLATE, templateId, actor);
        return getSettings();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicSettingsResponse getPublicSettings() {
        SystemSettingsDto settings = getSettings();
        return new PublicSettingsResponse(settings.maxUploadSizeMb(), settings.allowedExtensions());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinutesTemplateResponse> listTemplates() {
        String defaultId = read(KEY_DEFAULT_TEMPLATE, String.class, "");
        return cmFileRepository.findByTypeOrderByCreateDateDesc(TEMPLATE_FILE_TYPE).stream()
                .map(f -> new MinutesTemplateResponse(
                        f.getId(), f.getFileName(), f.getFileSize(), f.getId().equals(defaultId),
                        f.getCreateBy(), f.getCreateDate()))
                .toList();
    }

    @Override
    @Transactional
    public MinutesTemplateResponse uploadTemplate(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file mẫu biên bản.");
        }
        String originalName = cleanFileName(file.getOriginalFilename());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("Mẫu biên bản phải là file .docx.");
        }
        if (file.getSize() > MAX_TEMPLATE_BYTES) {
            throw new IllegalArgumentException("Mẫu biên bản không vượt quá 10 MB.");
        }

        try (InputStream is = file.getInputStream()) {
            List<String> missing = DocxPlaceholderInspector.missingPlaceholders(is);
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Mẫu thiếu placeholder bắt buộc: " + String.join(", ", missing));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file mẫu.", e);
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        String objectName = "templates/minutes/" + id + ".docx";
        String contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        try (InputStream is = file.getInputStream()) {
            storageService.upload(objectName, is, file.getSize(), contentType);
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file mẫu.", e);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        CmFile entity = new CmFile();
        entity.setId(id);
        entity.setFileName(originalName);
        entity.setFileSize(BigDecimal.valueOf(file.getSize()));
        entity.setMimeType(contentType);
        entity.setExtension(".docx");
        entity.setType(TEMPLATE_FILE_TYPE);
        entity.setIcon("doc");
        entity.setReferenceFileId(TEMPLATE_REFERENCE);
        entity.setMinutesFile(false);
        entity.setOrderNumber(0);
        entity.setVoiceToText("");
        entity.setBucketName(storageService.getBucketName());
        entity.setObjectName(objectName);
        entity.setCreateBy(actor);
        entity.setCreateDate(now);
        entity.setUpdateBy(actor);
        entity.setUpdateDate(now);
        try {
            cmFileRepository.save(entity);
        } catch (RuntimeException ex) {
            storageService.delete(objectName);
            throw ex;
        }

        // Mẫu đầu tiên tự thành mặc định.
        if (read(KEY_DEFAULT_TEMPLATE, String.class, "").isEmpty()) {
            write(KEY_DEFAULT_TEMPLATE, id, actor);
        }
        String defaultId = read(KEY_DEFAULT_TEMPLATE, String.class, "");
        return new MinutesTemplateResponse(id, originalName, entity.getFileSize(), id.equals(defaultId), actor, now);
    }

    @Override
    @Transactional
    public void setDefaultTemplate(String id, String actor) {
        if (!isTemplate(id)) {
            throw new NoSuchElementException("Không tìm thấy mẫu biên bản.");
        }
        write(KEY_DEFAULT_TEMPLATE, id, actor);
    }

    @Override
    @Transactional
    public void deleteTemplate(String id) {
        CmFile file = findTemplate(id);
        if (id.equals(read(KEY_DEFAULT_TEMPLATE, String.class, ""))) {
            throw new IllegalStateException("Không thể xóa mẫu đang được đặt làm mặc định.");
        }
        cmFileRepository.delete(file);
        storageService.delete(file.getObjectName());
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResponse getTemplateDownload(String id) {
        CmFile file = findTemplate(id);
        return new FileDownloadResponse(storageService.getDownloadUrl(file.getObjectName(), 900), 900);
    }

    /** Chuẩn hóa đuôi file: chữ thường, có dấu chấm, bỏ trùng, chặn đuôi thực thi được. */
    static List<String> normalizeExtensions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất một loại tệp.");
        }
        List<String> result = raw.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .map(e -> e.startsWith(".") ? e : "." + e)
                .distinct()
                .toList();
        for (String ext : result) {
            if (!EXTENSION.matcher(ext).matches()) {
                throw new IllegalArgumentException("Đuôi tệp không hợp lệ: " + ext);
            }
            if (BLOCKED_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException("Không cho phép loại tệp có thể thực thi: " + ext);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất một loại tệp.");
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<String> templateObjectName(String preferredTemplateId) {
        String id = isTemplate(preferredTemplateId) ? preferredTemplateId : read(KEY_DEFAULT_TEMPLATE, String.class, "");
        if (id == null || id.isBlank()) {
            return java.util.Optional.empty();
        }
        return cmFileRepository.findById(id).filter(f -> f.getType() == TEMPLATE_FILE_TYPE).map(CmFile::getObjectName);
    }

    private boolean isTemplate(String id) {
        return id != null && cmFileRepository.findById(id).filter(f -> f.getType() == TEMPLATE_FILE_TYPE).isPresent();
    }

    private CmFile findTemplate(String id) {
        return cmFileRepository.findById(id)
                .filter(f -> f.getType() == TEMPLATE_FILE_TYPE)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy mẫu biên bản."));
    }

    private <T> T read(String key, Class<T> type, T fallback) {
        return settingRepository.findById(key).map(s -> {
            try {
                return objectMapper.readValue(s.getValueJson(), type);
            } catch (JsonProcessingException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private <T> T read(String key, TypeReference<T> type, T fallback) {
        return settingRepository.findById(key).map(s -> {
            try {
                return objectMapper.readValue(s.getValueJson(), type);
            } catch (JsonProcessingException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private void write(String key, Object value, String actor) {
        SySetting setting = settingRepository.findById(key).orElseGet(() -> {
            SySetting s = new SySetting();
            s.setKey(key);
            return s;
        });
        try {
            setting.setValueJson(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Giá trị cấu hình không hợp lệ.", e);
        }
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(LocalDateTime.now(clock));
        settingRepository.save(setting);
    }

    private static String cleanFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return lastSlash >= 0 ? fileName.substring(lastSlash + 1) : fileName;
    }
}
