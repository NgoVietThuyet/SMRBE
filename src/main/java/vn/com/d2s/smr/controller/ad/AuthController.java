package vn.com.d2s.smr.controller.ad;

import vn.com.d2s.smr.config.audit.Audited;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.config.AuthProperties;
import vn.com.d2s.smr.dto.ad.auth.AuthActionResult;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.auth.ChangePasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordResult;
import vn.com.d2s.smr.dto.ad.auth.GuestSessionRequest;
import vn.com.d2s.smr.dto.ad.auth.LoginRequest;
import vn.com.d2s.smr.dto.ad.auth.LogoutRequest;
import vn.com.d2s.smr.dto.ad.auth.RefreshTokenRequest;
import vn.com.d2s.smr.dto.ad.auth.RegisterRequest;
import vn.com.d2s.smr.dto.ad.auth.ResetPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.UpdateProfileRequest;
import vn.com.d2s.smr.dto.ad.auth.UserProfileResponse;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.service.ad.AuthService;

import java.security.Principal;

@RestController
@RequestMapping("/api/Auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @PostMapping("/Register")
    public ResponseEntity<ApiResponse<AuthResultResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        if (!authProperties.publicRegistration()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("Hệ thống không kích hoạt tính năng đăng ký công khai."));
        }
        AuthResultResponse result = authService.register(request);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }

    @Audited(category = "AUTH", action = "LOGIN", targetType = "ACCOUNT", targetBodyField = "userName")
    @PostMapping("/Login")
    public ResponseEntity<ApiResponse<AuthResultResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResultResponse result = authService.login(request);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }

    @PostMapping("/Refresh")
    public ResponseEntity<ApiResponse<AuthResultResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResultResponse result = authService.refresh(request);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }

    @GetMapping("/Profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> profile(Principal principal) {
        return authService.getProfile(principal.getName())
                .map(profile -> ResponseEntity.ok(ApiResponse.success(profile, null)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("Không tìm thấy thông tin người dùng.")));
    }

    @Audited(category = "ACCOUNT", action = "UPDATE_PROFILE")
    @PutMapping("/Profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return authService.updateProfile(principal.getName(), request)
                .map(profile -> ResponseEntity.ok(ApiResponse.success(
                        profile,
                        "Cập nhật thông tin cá nhân thành công."
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure("Không tìm thấy thông tin người dùng.")));
    }

    @Audited(category = "AUTH", action = "CHANGE_PASSWORD")
    @PostMapping("/ChangePassword")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return actionResponse(authService.changePassword(principal.getName(), request));
    }

    @Audited(category = "AUTH", action = "LOGOUT")
    @PostMapping("/Logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            Principal principal,
            @Valid @RequestBody LogoutRequest request
    ) {
        return actionResponse(authService.logout(principal.getName(), request));
    }

    @Audited(category = "AUTH", action = "FORGOT_PASSWORD", targetType = "EMAIL", targetBodyField = "email")
    @PostMapping("/ForgotPassword")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        ForgotPasswordResult result = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(result.resetToken(), result.message()));
    }

    @Audited(category = "AUTH", action = "RESET_PASSWORD")
    @PostMapping("/ResetPassword")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return actionResponse(authService.resetPassword(request));
    }

    @PostMapping("/GuestSession")
    public ResponseEntity<ApiResponse<AuthResultResponse>> guestSession(
            @Valid @RequestBody GuestSessionRequest request
    ) {
        AuthResultResponse result = authService.guestSession(request);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }

    private static ResponseEntity<ApiResponse<Void>> actionResponse(AuthActionResult result) {
        if (!result.success()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(result.message()));
        }
        return ResponseEntity.ok(ApiResponse.success(null, result.message()));
    }
}
