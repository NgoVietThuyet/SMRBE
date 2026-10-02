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
import vn.com.d2s.smr.dto.ad.auth.AuthActionResult;
import vn.com.d2s.smr.dto.ad.auth.ChangePasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordResult;
import vn.com.d2s.smr.dto.ad.auth.LogoutRequest;
import vn.com.d2s.smr.dto.ad.auth.RegisterRequest;
import vn.com.d2s.smr.dto.ad.auth.ResetPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.UpdateProfileRequest;
import vn.com.d2s.smr.dto.ad.auth.UserProfileResponse;
import vn.com.d2s.smr.service.ad.AuthService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthModuleControllerTest {

    @Mock private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        configureController(new AuthProperties(false, "", "", false));
    }

    @Test
    void registerIsNotFoundWhenPublicRegistrationIsDisabled() throws Exception {
        mockMvc.perform(post("/api/Auth/Register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userName":"user","password":"password-123","fullName":"User",
                                 "email":"user@example.com","phone":""}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false));

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    void profileReturnsFrontendContract() throws Exception {
        when(authService.getProfile("admin")).thenReturn(Optional.of(new UserProfileResponse(
                "admin", "Quản trị viên", "admin@example.com", "0909", "Hà Nội", "Kỹ thuật", "Kỹ sư"
        )));

        mockMvc.perform(get("/api/Auth/Profile").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.userName").value("admin"))
                .andExpect(jsonPath("$.data.organizationName").value("Kỹ thuật"))
                .andExpect(jsonPath("$.data.titleName").value("Kỹ sư"));
    }

    @Test
    void profileReturnsNotFoundForMissingAccount() throws Exception {
        when(authService.getProfile("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/Auth/Profile").principal(() -> "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy thông tin người dùng."));
    }

    @Test
    void updateProfileReturnsUpdatedProfile() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(
                "admin", "Admin mới", "new@example.com", "0909", "Hà Nội", "Kỹ thuật", "Kỹ sư"
        );
        when(authService.updateProfile(org.mockito.ArgumentMatchers.eq("admin"), any(UpdateProfileRequest.class)))
                .thenReturn(Optional.of(profile));

        mockMvc.perform(put("/api/Auth/Profile")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Admin mới","email":"new@example.com",
                                 "phone":"0909","address":"Hà Nội"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cập nhật thông tin cá nhân thành công."))
                .andExpect(jsonPath("$.data.email").value("new@example.com"));
    }

    @Test
    void changePasswordReturnsBusinessFailureAsBadRequest() throws Exception {
        when(authService.changePassword(org.mockito.ArgumentMatchers.eq("admin"), any(ChangePasswordRequest.class)))
                .thenReturn(AuthActionResult.failure("Mật khẩu hiện tại không chính xác."));

        mockMvc.perform(post("/api/Auth/ChangePassword")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không chính xác."));
    }

    @Test
    void logoutReturnsNullDataEnvelope() throws Exception {
        when(authService.logout(org.mockito.ArgumentMatchers.eq("admin"), any(LogoutRequest.class)))
                .thenReturn(AuthActionResult.success("Đăng xuất thành công."));

        mockMvc.perform(post("/api/Auth/Logout")
                        .principal(() -> "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logoutAllDevices\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công."));
    }

    @Test
    void forgotPasswordNeverExposesTokenByDefault() throws Exception {
        when(authService.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenReturn(new ForgotPasswordResult("Yêu cầu đã được tiếp nhận.", null));

        mockMvc.perform(post("/api/Auth/ForgotPassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Yêu cầu đã được tiếp nhận."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void resetPasswordReturnsSuccessEnvelope() throws Exception {
        when(authService.resetPassword(any(ResetPasswordRequest.class)))
                .thenReturn(AuthActionResult.success(
                        "Đặt lại mật khẩu thành công. Các phiên truy cập cũ đã được thu hồi."
                ));

        mockMvc.perform(post("/api/Auth/ResetPassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"token\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
    }

    private void configureController(AuthProperties properties) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }
}
