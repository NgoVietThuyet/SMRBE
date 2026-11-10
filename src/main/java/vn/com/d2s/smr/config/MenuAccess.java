package vn.com.d2s.smr.config;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.service.ad.PermissionService;

/**
 * Quyền vào từng menu Quản trị (ADMIN_*): Admin luôn được; tài khoản khác được khi quyền hiệu lực (tài khoản → chức danh →
 * phòng ban) của họ cho phép mã đó. Đọc từ DB mỗi request nên thu hồi quyền có hiệu lực ngay.
 */
@Component
public class MenuAccess {

    private final AdminAccess adminAccess;
    private final PermissionService permissionService;

    public MenuAccess(AdminAccess adminAccess, PermissionService permissionService) {
        this.adminAccess = adminAccess;
        this.permissionService = permissionService;
    }

    public AuthorizationManager<RequestAuthorizationContext> requires(String code) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
                return new AuthorizationDecision(false);
            }
            return new AuthorizationDecision(adminAccess.isAdmin(auth.getName()) || has(auth.getName(), code));
        };
    }

    boolean has(String userName, String code) {
        try {
            return permissionService.getEffectivePermissions(userName).stream()
                    .anyMatch(p -> code.equalsIgnoreCase(p.code()) && p.allowed());
        } catch (RuntimeException e) {
            return false; // tài khoản không tồn tại (token khách...) hoặc dữ liệu quyền lỗi
        }
    }
}
