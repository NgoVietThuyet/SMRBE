package vn.com.d2s.smr.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Chỉ cho tài khoản có role SUPER_ADMIN (AdAccounts.RoleCodes) gọi các API quản trị.
 * Đọc role từ DB mỗi request để thu hồi quyền Admin có hiệu lực ngay, không phụ thuộc token cũ.
 */
@Component
public class AdminAccess implements AuthorizationManager<RequestAuthorizationContext> {

    static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdAccountRepository accountRepository;

    public AdminAccess(AdAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(isAdmin(auth.getName()));
    }

    private vn.com.d2s.smr.service.ad.PermissionService permissionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPermissionService(vn.com.d2s.smr.service.ad.PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** Admin = role SUPER_ADMIN, hoặc được gán quyền ADMIN (tài khoản → chức danh → phòng ban). */
    public boolean isAdmin(String userName) {
        var account = accountRepository.findById(userName).orElse(null);
        if (account == null) {
            return false;
        }
        if (account.getRoleCodes() != null && account.getRoleCodes().toUpperCase(Locale.ROOT).contains(SUPER_ADMIN)) {
            return true;
        }
        if (permissionService == null) {
            return false;
        }
        try {
            return permissionService.getEffectivePermissions(userName).stream()
                    .anyMatch(p -> "ADMIN".equalsIgnoreCase(p.code()) && p.allowed());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
