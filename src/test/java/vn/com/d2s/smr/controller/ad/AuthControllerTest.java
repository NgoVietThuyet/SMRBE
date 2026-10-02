package vn.com.d2s.smr.controller.ad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vn.com.d2s.smr.config.AuthProperties;
import vn.com.d2s.smr.controller.common.GlobalExceptionHandler;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.auth.LoginRequest;
import vn.com.d2s.smr.dto.ad.auth.RefreshTokenRequest;
import vn.com.d2s.smr.service.ad.AuthService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        authService,
                        new AuthProperties(false, "", "", false)
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void loginReturnsCompatibleSuccessEnvelope() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResultResponse(
                true,
                "Đăng nhập thành công.",
                "access-token",
                "admin",
                "Quản trị viên",
                "admin@example.com",
                false,
                "refresh-token"
        ));

        mockMvc.perform(post("/api/Auth/Login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công."))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.token").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.userName").value("admin"));
    }

    @Test
    void loginReturnsCompatibleFailureEnvelope() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(AuthResultResponse.failure("Mật khẩu không chính xác."));

        mockMvc.perform(post("/api/Auth/Login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Mật khẩu không chính xác."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void loginRejectsBlankCredentialsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/Auth/Login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Vui lòng nhập đầy đủ thông tin Tên đăng nhập và Mật khẩu."));

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void refreshReturnsRotatedTokenPairInCompatibleEnvelope() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(new AuthResultResponse(
                true,
                "Làm mới token thành công.",
                "new-access-token",
                "admin",
                "Quản trị viên",
                "admin@example.com",
                false,
                "new-refresh-token"
        ));

        mockMvc.perform(post("/api/Auth/Refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Làm mới token thành công."))
                .andExpect(jsonPath("$.data.token").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refreshRejectsBlankTokenBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/Auth/Refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Vui lòng cung cấp Refresh Token hợp lệ."));

        verify(authService, never()).refresh(any(RefreshTokenRequest.class));
    }

    @Test
    void refreshReturnsCompatibleFailureEnvelope() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(AuthResultResponse.failure("Refresh token không hợp lệ hoặc đã hết hạn."));

        mockMvc.perform(post("/api/Auth/Refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Refresh token không hợp lệ hoặc đã hết hạn."));
    }
}
