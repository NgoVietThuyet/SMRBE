package vn.com.d2s.smr.config.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import vn.com.d2s.smr.dto.sy.AuditEntry;
import vn.com.d2s.smr.service.ad.EmployeeService;
import vn.com.d2s.smr.service.sy.AuditService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi SyAuditLogs cho các method controller có @Audited, sau khi request hoàn tất.
 * Thành công = không có exception và HTTP status < 400 (đăng nhập sai trả 400 → ghi là thất bại).
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    static final String BEFORE_ATTRIBUTE = AuditInterceptor.class.getName() + ".BEFORE";

    private final AuditService auditService;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    public AuditInterceptor(AuditService auditService, EmployeeService employeeService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.employeeService = employeeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Audited audited = audited(handler);
        if (audited != null && audited.snapshotPermission()) {
            Map<String, String> vars = pathVariables(request);
            try {
                Object before = employeeService.getPermission(vars.get("type"), vars.get("id"));
                request.setAttribute(BEFORE_ATTRIBUTE, before);
            } catch (RuntimeException ignored) {
                // Đối tượng không tồn tại → controller sẽ trả lỗi; nhật ký vẫn ghi thất bại.
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Audited audited = audited(handler);
        if (audited == null) {
            return;
        }

        Map<String, String> vars = pathVariables(request);
        Map<String, Object> body = toMap(request.getAttribute(AuditRequestBodyAdvice.BODY_ATTRIBUTE));

        Map<String, Object> payload = new LinkedHashMap<>();
        if (!vars.isEmpty()) payload.put("path", vars);
        if (body != null) payload.put("request", body);
        Object before = request.getAttribute(BEFORE_ATTRIBUTE);
        if (before != null) payload.put("before", toMap(before));

        String targetId = null;
        if (!audited.targetPathVariable().isEmpty()) {
            targetId = vars.get(audited.targetPathVariable());
        } else if (!audited.targetBodyField().isEmpty() && body != null && body.get(audited.targetBodyField()) != null) {
            targetId = String.valueOf(body.get(audited.targetBodyField()));
        }

        String actor = currentUser();
        if (actor == null && body != null && body.get("userName") != null) {
            actor = String.valueOf(body.get("userName"));
        }

        int status = response.getStatus();
        auditService.log(new AuditEntry(
                audited.category(),
                audited.action(),
                actor,
                audited.targetType().isEmpty() ? null : audited.targetType(),
                targetId,
                ex == null && status < 400,
                status,
                clientIp(request),
                request.getHeader("User-Agent"),
                payload
        ));
    }

    private static Audited audited(Object handler) {
        return handler instanceof HandlerMethod method ? method.getMethodAnnotation(Audited.class) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return vars instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, Map.class);
        } catch (IllegalArgumentException e) {
            return Map.of("value", String.valueOf(value));
        }
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
