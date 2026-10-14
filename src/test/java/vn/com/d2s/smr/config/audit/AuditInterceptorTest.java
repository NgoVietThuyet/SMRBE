package vn.com.d2s.smr.config.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import vn.com.d2s.smr.dto.ad.permission.PermissionDocument;
import vn.com.d2s.smr.dto.sy.AuditEntry;
import vn.com.d2s.smr.service.ad.EmployeeService;
import vn.com.d2s.smr.service.sy.AuditService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditInterceptorTest {

    @Mock private AuditService auditService;
    @Mock private EmployeeService employeeService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void failedLoginIsLoggedWithActorFromBodyAndNoPassword() throws Exception {
        AuditInterceptor interceptor = new AuditInterceptor(auditService, employeeService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/Auth/Login");
        request.setAttribute(AuditRequestBodyAdvice.BODY_ATTRIBUTE, Map.of("userName", "admin", "password", "bad"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(400);

        interceptor.afterCompletion(request, response, handler("login"), null);

        AuditEntry entry = captured();
        assertThat(entry.category()).isEqualTo("AUTH");
        assertThat(entry.action()).isEqualTo("LOGIN");
        assertThat(entry.actorUserName()).isEqualTo("admin");
        assertThat(entry.targetId()).isEqualTo("admin");
        assertThat(entry.success()).isFalse();
        assertThat(entry.httpStatus()).isEqualTo(400);
    }

    @Test
    void adminActionUsesPrincipalPathVariableAndPermissionSnapshot() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("boss", null, "ROLE_X"));
        when(employeeService.getPermission("title", "DEV")).thenReturn(PermissionDocument.empty());
        AuditInterceptor interceptor = new AuditInterceptor(auditService, employeeService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/human-resources/title/DEV/permissions");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("type", "title", "id", "DEV"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);

        interceptor.preHandle(request, response, handler("savePermission"));
        interceptor.afterCompletion(request, response, handler("savePermission"), null);

        AuditEntry entry = captured();
        assertThat(entry.actorUserName()).isEqualTo("boss");
        assertThat(entry.targetId()).isEqualTo("DEV");
        assertThat(entry.success()).isTrue();
        assertThat(entry.payload()).containsKeys("path", "before");
    }

    @Test
    void methodsWithoutAnnotationAreIgnored() throws Exception {
        AuditInterceptor interceptor = new AuditInterceptor(auditService, employeeService, new ObjectMapper());

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), handler("plain"), null);

        verify(auditService, never()).log(org.mockito.ArgumentMatchers.any());
    }

    private AuditEntry captured() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).log(captor.capture());
        return captor.getValue();
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getMethod(name));
    }

    static class SampleController {
        @Audited(category = "AUTH", action = "LOGIN", targetType = "ACCOUNT", targetBodyField = "userName")
        public void login() {
        }

        @Audited(category = "PERMISSION", action = "UPDATE", targetType = "PERMISSION",
                targetPathVariable = "id", snapshotPermission = true)
        public void savePermission() {
        }

        public void plain() {
        }
    }
}
