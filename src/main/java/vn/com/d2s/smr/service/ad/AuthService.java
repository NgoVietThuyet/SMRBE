package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.auth.AuthActionResult;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.auth.ChangePasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordResult;
import vn.com.d2s.smr.dto.ad.auth.LoginRequest;
import vn.com.d2s.smr.dto.ad.auth.LogoutRequest;
import vn.com.d2s.smr.dto.ad.auth.RefreshTokenRequest;
import vn.com.d2s.smr.dto.ad.auth.RegisterRequest;
import vn.com.d2s.smr.dto.ad.auth.ResetPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.UpdateProfileRequest;
import vn.com.d2s.smr.dto.ad.auth.UserProfileResponse;

import java.util.Optional;

public interface AuthService {

    AuthResultResponse register(RegisterRequest request);

    AuthResultResponse login(LoginRequest request);

    AuthResultResponse refresh(RefreshTokenRequest request);

    Optional<UserProfileResponse> getProfile(String userName);

    Optional<UserProfileResponse> updateProfile(String userName, UpdateProfileRequest request);

    AuthActionResult changePassword(String userName, ChangePasswordRequest request);

    AuthActionResult logout(String userName, LogoutRequest request);

    ForgotPasswordResult forgotPassword(ForgotPasswordRequest request);

    AuthActionResult resetPassword(ResetPasswordRequest request);

    AuthResultResponse guestSession(vn.com.d2s.smr.dto.ad.auth.GuestSessionRequest request);
}
