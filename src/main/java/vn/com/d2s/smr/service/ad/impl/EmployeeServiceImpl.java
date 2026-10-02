package vn.com.d2s.smr.service.ad.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.ad.employee.ChangeStatusRequest;
import vn.com.d2s.smr.dto.ad.employee.ChangeTitleRequest;
import vn.com.d2s.smr.dto.ad.employee.CreateDirectoryEmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.CreatedDirectoryEmployeeResponse;
import vn.com.d2s.smr.dto.ad.employee.CreatedEmployeeResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeDetailResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeItemResponse;
import vn.com.d2s.smr.dto.ad.employee.EmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.ResetEmployeePasswordResponse;
import vn.com.d2s.smr.dto.ad.employee.TransferEmployeeRequest;
import vn.com.d2s.smr.dto.ad.employee.UpdateEmployeeRequest;
import vn.com.d2s.smr.dto.ad.permission.EffectivePermissionResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;
import vn.com.d2s.smr.dto.ad.permission.PermissionUpdateRequest;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.service.ad.EmployeeService;
import vn.com.d2s.smr.service.ad.PermissionService;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdAccountRepository adAccountRepository;
    private final MdOrganizeRepository mdOrganizeRepository;
    private final MdTitleRepository mdTitleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;

    public EmployeeServiceImpl(
            AdAccountRepository adAccountRepository,
            MdOrganizeRepository mdOrganizeRepository,
            MdTitleRepository mdTitleRepository,
            PasswordEncoder passwordEncoder,
            PermissionService permissionService
    ) {
        this.adAccountRepository = adAccountRepository;
        this.mdOrganizeRepository = mdOrganizeRepository;
        this.mdTitleRepository = mdTitleRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResultResponse<EmployeeItemResponse> searchEmployees(
            String org, String title, Boolean active, String keyword, int page, int pageSize
    ) {
        int validatedPage = Math.max(1, page);
        int validatedPageSize = Math.min(100, Math.max(1, pageSize));
        Pageable pageable = PageRequest.of(validatedPage - 1, validatedPageSize);

        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String cleanOrg = (org != null && !org.isBlank()) ? org.trim() : null;
        String cleanTitle = (title != null && !title.isBlank()) ? title.trim() : null;

        List<EmployeeItemResponse> items = adAccountRepository.findEmployees(cleanOrg, cleanTitle, active, cleanKeyword, pageable);
        long totalItems = adAccountRepository.countEmployees(cleanOrg, cleanTitle, active, cleanKeyword);
        int totalPages = (int) Math.ceil((double) totalItems / validatedPageSize);

        return new PagedResultResponse<>(items, validatedPage, validatedPageSize, totalItems, totalPages);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDetailResponse getEmployee(String userName) {
        AdAccount a = adAccountRepository.findById(userName)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        String orgName = null;
        if (a.getOrgId() != null && !a.getOrgId().isEmpty()) {
            orgName = mdOrganizeRepository.findById(a.getOrgId()).map(MdOrganize::getName).orElse(null);
        }
        String titleName = null;
        if (a.getTitleCode() != null && !a.getTitleCode().isEmpty()) {
            titleName = mdTitleRepository.findById(a.getTitleCode()).map(MdTitle::getName).orElse(null);
        }
        return new EmployeeDetailResponse(
                a.getUserName(),
                a.getFullName(),
                a.getEmail(),
                a.getPhone(),
                a.getAddress(),
                a.getOrgId(),
                orgName,
                a.getTitleCode(),
                titleName,
                a.isActive(),
                a.isMustChangePassword(),
                a.getLastLoginAt(),
                a.getCreateDate(),
                a.getUpdateDate()
        );
    }

    @Override
    @Transactional
    public CreatedEmployeeResponse createEmployee(EmployeeRequest r, String actor) {
        validateOrgTitle(r.organizationId(), r.titleCode());
        String userName = r.userName().trim();
        String email = r.email().trim();
        if (adAccountRepository.existsByUserNameOrEmail(userName, email)) {
            throw new IllegalArgumentException("Username hoặc email đã tồn tại.");
        }
        String tempPassword = generatePassword();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        AdAccount a = new AdAccount();
        a.setUserName(userName);
        a.setPassword(passwordEncoder.encode(tempPassword));
        a.setFullName(r.fullName().trim());
        a.setEmail(email);
        a.setPhone(r.phone() != null ? r.phone().trim() : "");
        a.setAddress(r.address() != null ? r.address().trim() : "");
        a.setOrgId(r.organizationId());
        a.setTitleCode(r.titleCode());
        a.setActive(true);
        a.setMustChangePassword(true);
        a.setTokenVersion(1);
        a.setCreateBy(actor);
        a.setCreateDate(now);
        a.setUpdateBy(actor);
        a.setUpdateDate(now);

        adAccountRepository.save(a);
        return new CreatedEmployeeResponse(a.getUserName(), a.getFullName(), a.getEmail(), tempPassword, a.isMustChangePassword());
    }

    @Override
    @Transactional
    public CreatedDirectoryEmployeeResponse createDirectoryEmployee(CreateDirectoryEmployeeRequest r, String actor) {
        validateOrgTitle(r.organizationId(), r.titleCode());
        String email = r.email().trim();
        if (adAccountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        }
        String baseName = normalizeUserName(r.fullName());
        String userName = baseName;
        int i = 1;
        while (adAccountRepository.existsById(userName)) {
            userName = baseName + String.format("%02d", i++);
        }
        String rawPassword = baseName + "@123";
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        AdAccount a = new AdAccount();
        a.setUserName(userName);
        a.setPassword(passwordEncoder.encode(rawPassword));
        a.setFullName(r.fullName().trim());
        a.setEmail(email);
        a.setPhone(r.phone() != null ? r.phone().trim() : "");
        a.setAddress(r.address() != null ? r.address().trim() : "");
        a.setOrgId(r.organizationId());
        a.setTitleCode(r.titleCode());
        a.setActive(true);
        a.setMustChangePassword(true);
        a.setTokenVersion(1);
        a.setCreateBy(actor);
        a.setCreateDate(now);
        a.setUpdateBy(actor);
        a.setUpdateDate(now);

        adAccountRepository.save(a);
        return new CreatedDirectoryEmployeeResponse(userName);
    }

    @Override
    @Transactional
    public void updateEmployee(String userName, UpdateEmployeeRequest r, String actor) {
        validateOrgTitle(r.organizationId(), r.titleCode());
        AdAccount a = adAccountRepository.findById(userName)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        String email = r.email().trim();
        if (adAccountRepository.existsByEmailAndUserNameNot(email, userName)) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        }
        a.setFullName(r.fullName().trim());
        a.setEmail(email);
        a.setPhone(r.phone() != null ? r.phone().trim() : "");
        a.setAddress(r.address() != null ? r.address().trim() : "");
        a.setOrgId(r.organizationId());
        a.setTitleCode(r.titleCode());
        a.setTokenVersion(a.getTokenVersion() + 1);
        a.setUpdateBy(actor);
        a.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));

        adAccountRepository.save(a);
    }

    @Override
    @Transactional
    public void transferEmployee(String userName, TransferEmployeeRequest r, String actor) {
        if (!mdOrganizeRepository.existsByIdAndActiveTrue(r.organizationId())) {
            throw new IllegalArgumentException("Phòng ban không hợp lệ.");
        }
        AdAccount a = adAccountRepository.findById(userName)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        a.setOrgId(r.organizationId());
        a.setTokenVersion(a.getTokenVersion() + 1);
        a.setUpdateBy(actor);
        a.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));

        adAccountRepository.save(a);
    }

    @Override
    @Transactional
    public void changeEmployeeTitle(String userName, ChangeTitleRequest r, String actor) {
        if (!mdTitleRepository.existsByCodeAndActiveTrue(r.titleCode())) {
            throw new IllegalArgumentException("Chức danh không hợp lệ.");
        }
        AdAccount a = adAccountRepository.findById(userName)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        a.setTitleCode(r.titleCode());
        a.setTokenVersion(a.getTokenVersion() + 1);
        a.setUpdateBy(actor);
        a.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));

        adAccountRepository.save(a);
    }

    @Override
    @Transactional
    public void changeEmployeeStatus(String userName, ChangeStatusRequest r, String actor) {
        if (userName.equals(actor) && !r.active()) {
            throw new IllegalStateException("Không thể tự khóa tài khoản đang đăng nhập.");
        }
        AdAccount a = adAccountRepository.findById(userName)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        a.setActive(r.active());
        a.setTokenVersion(a.getTokenVersion() + 1);
        a.setUpdateBy(actor);
        a.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));

        adAccountRepository.save(a);
    }

    @Override
    @Transactional
    public ResetEmployeePasswordResponse resetEmployeePassword(String userName, String actor) {
        AdAccount a = adAccountRepository.findById(userName)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        String tempPassword = generatePassword();
        a.setPassword(passwordEncoder.encode(tempPassword));
        a.setMustChangePassword(true);
        a.setTokenVersion(a.getTokenVersion() + 1);
        a.setUpdateBy(actor);
        a.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));

        adAccountRepository.save(a);
        return new ResetEmployeePasswordResponse(userName, tempPassword);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionDocument getPermission(String type, String id) {
        String json = switch (type.toLowerCase(Locale.ROOT)) {
            case "organization" -> mdOrganizeRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phòng ban.")).getPermissionJson();
            case "title" -> mdTitleRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chức danh.")).getPermissionJson();
            case "employee" -> adAccountRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản.")).getPermissionJson();
            default -> throw new IllegalArgumentException("Đối tượng phân quyền không hợp lệ.");
        };
        return permissionService.parseAndValidate(json);
    }

    @Override
    @Transactional
    public void savePermission(String type, String id, PermissionUpdateRequest r, String actor) {
        PermissionDocument doc = new PermissionDocument(r.version(), r.permissions());
        String json = permissionService.serializeAndValidate(doc);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        switch (type.toLowerCase(Locale.ROOT)) {
            case "organization" -> {
                MdOrganize o = mdOrganizeRepository.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phòng ban."));
                o.setPermissionJson(json);
                o.setUpdateBy(actor);
                o.setUpdateDate(now);
                mdOrganizeRepository.save(o);
            }
            case "title" -> {
                MdTitle t = mdTitleRepository.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chức danh."));
                t.setPermissionJson(json);
                t.setUpdateBy(actor);
                t.setUpdateDate(now);
                mdTitleRepository.save(t);
            }
            case "employee" -> {
                AdAccount a = adAccountRepository.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
                a.setPermissionJson(json);
                a.setTokenVersion(a.getTokenVersion() + 1);
                a.setUpdateBy(actor);
                a.setUpdateDate(now);
                adAccountRepository.save(a);
            }
            default -> throw new IllegalArgumentException("Đối tượng phân quyền không hợp lệ.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EffectivePermissionResponse> getEffectivePermissions(String userName) {
        if (!adAccountRepository.existsById(userName)) {
            throw new NoSuchElementException("Không tìm thấy tài khoản.");
        }
        return permissionService.getEffectivePermissions(userName);
    }

    public static String normalizeUserName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "user";
        }
        String[] parts = fullName.trim().split("[\\s\\t]+");
        String last = parts.length > 0 ? parts[parts.length - 1] : "";
        String nfd = Normalizer.normalize(last, Normalizer.Form.NFD);
        String noMarks = nfd.replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase(Locale.ROOT);
        String clean = noMarks.replaceAll("[^a-z0-9]", "");
        if (clean.isEmpty()) {
            clean = "user";
        }
        if (clean.length() > 50) {
            clean = clean.substring(0, 50);
        }
        return clean;
    }

    private void validateOrgTitle(String org, String title) {
        if (!mdOrganizeRepository.existsByIdAndActiveTrue(org)) {
            throw new IllegalArgumentException("Phòng ban không hợp lệ.");
        }
        if (!mdTitleRepository.existsByCodeAndActiveTrue(title)) {
            throw new IllegalArgumentException("Chức danh không hợp lệ.");
        }
    }

    private static String generatePassword() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
