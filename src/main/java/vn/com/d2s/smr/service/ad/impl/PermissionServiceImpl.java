package vn.com.d2s.smr.service.ad.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDefinition;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;
import vn.com.d2s.smr.dto.ad.permission.PermissionEffect;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.service.ad.PermissionCatalog;
import vn.com.d2s.smr.service.ad.PermissionService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final AdAccountRepository accountRepository;
    private final MdTitleRepository titleRepository;
    private final MdOrganizeRepository organizeRepository;
    private final ObjectMapper objectMapper;

    public PermissionServiceImpl(
            AdAccountRepository accountRepository,
            MdTitleRepository titleRepository,
            MdOrganizeRepository organizeRepository,
            ObjectMapper objectMapper
    ) {
        this.accountRepository = accountRepository;
        this.titleRepository = titleRepository;
        this.organizeRepository = organizeRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(String userName, String code) {
        AdAccount account = accountRepository.findById(userName).filter(AdAccount::isActive).orElse(null);
        if (account == null) {
            return false;
        }
        if (isSuperAdmin(account.getRoleCodes())) {
            return true;
        }
        MdTitle title = titleRepository.findById(account.getTitleCode()).orElse(null);
        MdOrganize organization = organizeRepository.findById(account.getOrgId()).orElse(null);
        return resolve(code, account, title, organization).allowed();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EffectivePermissionResponse> getEffectivePermissions(String userName) {
        AdAccount account = accountRepository.findById(userName)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        MdTitle title = titleRepository.findById(account.getTitleCode()).orElse(null);
        MdOrganize organization = organizeRepository.findById(account.getOrgId()).orElse(null);

        return PermissionCatalog.DEFINITIONS.stream()
                .map(definition -> toEffectivePermission(definition, account, title, organization))
                .toList();
    }

    @Override
    public PermissionDocument parseAndValidate(String json) {
        if (json == null || json.isBlank()) {
            return PermissionDocument.empty();
        }
        try {
            PermissionDocument document = objectMapper.readValue(json, PermissionDocument.class);
            validate(document);
            return normalize(document);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("JSON quyền không hợp lệ.", exception);
        }
    }

    @Override
    public String serializeAndValidate(PermissionDocument document) {
        validate(document);
        try {
            return objectMapper.writeValueAsString(normalize(document));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON quyền không hợp lệ.", exception);
        }
    }

    private EffectivePermissionResponse toEffectivePermission(
            PermissionDefinition definition,
            AdAccount account,
            MdTitle title,
            MdOrganize organization
    ) {
        if (isSuperAdmin(account.getRoleCodes())) {
            return new EffectivePermissionResponse(
                    definition.code(), definition.name(), true, "Allow",
                    "ROLE", "SUPER_ADMIN", "Quản trị hệ thống"
            );
        }

        Resolution resolution = resolve(definition.code(), account, title, organization);
        String sourceId = switch (resolution.sourceType()) {
            case "USER" -> account.getUserName();
            case "TITLE" -> title == null ? null : title.getCode();
            case "ORGANIZATION" -> organization == null ? null : organization.getId();
            default -> null;
        };
        String sourceName = switch (resolution.sourceType()) {
            case "USER" -> account.getFullName();
            case "TITLE" -> title == null ? null : title.getName();
            case "ORGANIZATION" -> organization == null ? null : organization.getName();
            default -> null;
        };
        return new EffectivePermissionResponse(
                definition.code(),
                definition.name(),
                resolution.allowed(),
                resolution.effect().wireValue(),
                resolution.sourceType(),
                sourceId,
                sourceName
        );
    }

    private Resolution resolve(String code, AdAccount account, MdTitle title, MdOrganize organization) {
        List<PermissionSource> sources = List.of(
                new PermissionSource(account.getPermissionJson(), "USER"),
                new PermissionSource(title == null ? null : title.getPermissionJson(), "TITLE"),
                new PermissionSource(organization == null ? null : organization.getPermissionJson(), "ORGANIZATION")
        );
        for (PermissionSource source : sources) {
            PermissionDocument document;
            try {
                document = parseAndValidate(source.json());
            } catch (IllegalArgumentException exception) {
                return new Resolution(false, PermissionEffect.DENY, "INVALID_JSON");
            }
            PermissionEffect effect = document.permissions().get(code.toUpperCase(Locale.ROOT));
            if (effect != null && effect != PermissionEffect.INHERIT) {
                return new Resolution(effect == PermissionEffect.ALLOW, effect, source.type());
            }
        }
        return new Resolution(false, PermissionEffect.DENY, "DEFAULT");
    }

    private static void validate(PermissionDocument document) {
        if (document == null || document.version() != 1) {
            throw new IllegalArgumentException("Phiên bản permission JSON không được hỗ trợ.");
        }
        List<String> invalid = document.permissions().keySet().stream()
                .filter(code -> !PermissionCatalog.ALL_CODES.contains(code.toUpperCase(Locale.ROOT)))
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Mã quyền không hợp lệ: " + String.join(", ", invalid));
        }
    }

    private static PermissionDocument normalize(PermissionDocument document) {
        Map<String, PermissionEffect> normalized = new LinkedHashMap<>();
        document.permissions().forEach((code, effect) ->
                normalized.put(code.toUpperCase(Locale.ROOT), effect)
        );
        return new PermissionDocument(document.version(), normalized);
    }

    private static boolean isSuperAdmin(String roles) {
        return roles != null && roles.toUpperCase(Locale.ROOT).contains("SUPER_ADMIN");
    }

    private record PermissionSource(String json, String type) {
    }

    private record Resolution(boolean allowed, PermissionEffect effect, String sourceType) {
    }
}
