package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.com.d2s.smr.config.AuthProperties;
import vn.com.d2s.smr.dto.ad.auth.AuthActionResult;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.auth.ChangePasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.ForgotPasswordResult;
import vn.com.d2s.smr.dto.ad.auth.LogoutRequest;
import vn.com.d2s.smr.dto.ad.auth.RegisterRequest;
import vn.com.d2s.smr.dto.ad.auth.ResetPasswordRequest;
import vn.com.d2s.smr.dto.ad.auth.UpdateProfileRequest;
import vn.com.d2s.smr.dto.ad.auth.UserProfileResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.ad.PasswordResetNotifier;
import vn.com.d2s.smr.service.ad.ResetTokenIdentity;
import vn.com.d2s.smr.service.ad.TokenPair;
import vn.com.d2s.smr.service.ad.TokenService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthModuleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T01:00:00Z");

    @Mock private AdAccountRepository accountRepository;
    @Mock private MdOrganizeRepository organizeRepository;
    @Mock private MdTitleRepository titleRepository;
    @Mock private MeetingInfoRepository meetingInfoRepository;
    @Mock private MeetingPersonalRepository meetingPersonalRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private PasswordResetNotifier passwordResetNotifier;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = service(new AuthProperties(true, "ORG", "STAFF", false));
    }

    @Test
    void registerRejectsWhenPublicRegistrationDisabled() {
        AuthServiceImpl service = service(new AuthProperties(false, "ORG", "STAFF", false));

        AuthResultResponse result = service.register(new RegisterRequest("user", "Secret123!", "Tên", "a@b.com", "0909"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Hệ thống không kích hoạt tính năng đăng ký công khai.");
    }

    @Test
    void registerRejectsAdminTitleCode() {
        AuthServiceImpl service = service(new AuthProperties(true, "ORG", "ADMIN", false));

        AuthResultResponse result = service.register(new RegisterRequest("user", "Secret123!", "Tên", "a@b.com", "0909"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Cấu hình đăng ký công khai chưa hợp lệ.");
    }

    @Test
    void registerSavesAccountWithTokenVersion1AndReturnsTokens() {
        when(accountRepository.existsByUserNameOrEmail("newuser", "newuser@example.com")).thenReturn(false);
        when(organizeRepository.findById("ORG")).thenReturn(Optional.of(activeOrg()));
        when(titleRepository.findById("STAFF")).thenReturn(Optional.of(activeTitle()));
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed-pass");
        when(tokenService.issueFor(any())).thenReturn(new TokenPair("access-token", "refresh-token"));

        AuthResultResponse result = authService.register(
                new RegisterRequest("newuser", "Secret123!", "Người dùng mới", "newuser@example.com", "0909")
        );

        assertThat(result.success()).isTrue();
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<AdAccount> captor = ArgumentCaptor.forClass(AdAccount.class);
        verify(accountRepository).save(captor.capture());
        AdAccount saved = captor.getValue();
        assertThat(saved.getUserName()).isEqualTo("newuser");
        assertThat(saved.getPassword()).isEqualTo("hashed-pass");
        assertThat(saved.getTokenVersion()).isEqualTo(1);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isMustChangePassword()).isFalse();
    }

    @Test
    void getProfileReturnsMappedNames() {
        AdAccount account = activeAccount();
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        when(organizeRepository.findById("ORG")).thenReturn(Optional.of(activeOrg()));
        when(titleRepository.findById("STAFF")).thenReturn(Optional.of(activeTitle()));

        Optional<UserProfileResponse> profile = authService.getProfile("admin");

        assertThat(profile).isPresent();
        assertThat(profile.get().userName()).isEqualTo("admin");
        assertThat(profile.get().organizationName()).isEqualTo("Ban Giám đốc");
        assertThat(profile.get().titleName()).isEqualTo("Chuyên viên");
    }

    @Test
    void updateProfileRejectsDuplicateEmail() {
        AdAccount account = activeAccount();
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        when(accountRepository.existsByEmailAndUserNameNot("used@example.com", "admin")).thenReturn(true);

        assertThatThrownBy(() -> authService.updateProfile("admin", new UpdateProfileRequest("Tên mới", "used@example.com", "0909", "HN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email đã được sử dụng");
    }

    @Test
    void changePasswordIncrementsTokenVersion() {
        AdAccount account = activeAccount();
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-pass", "hashed-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        AuthActionResult result = authService.changePassword(
                "admin",
                new ChangePasswordRequest("current-pass", "new-password-123")
        );

        assertThat(result.success()).isTrue();
        assertThat(account.getPassword()).isEqualTo("new-hash");
        assertThat(account.getTokenVersion()).isEqualTo(2);
        assertThat(account.isMustChangePassword()).isFalse();
        verify(accountRepository).save(account);
    }

    @Test
    void logoutAllDevicesIncrementsTokenVersion() {
        AdAccount account = activeAccount();
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));

        AuthActionResult result = authService.logout("admin", new LogoutRequest(true));

        assertThat(result.success()).isTrue();
        assertThat(account.getTokenVersion()).isEqualTo(2);
        verify(accountRepository).save(account);
    }

    @Test
    void forgotPasswordReturnsTokenWhenExposed() {
        AuthServiceImpl service = service(new AuthProperties(true, "ORG", "STAFF", true));
        AdAccount account = activeAccount();
        when(accountRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(account));
        when(tokenService.issueResetToken(account)).thenReturn("reset-token");

        ForgotPasswordResult result = service.forgotPassword(new ForgotPasswordRequest("admin@example.com"));

        assertThat(result.resetToken()).isEqualTo("reset-token");
        verify(passwordResetNotifier).send(account, "reset-token");
    }

    @Test
    void resetPasswordUpdatesPasswordAndIncrementsTokenVersion() {
        AdAccount account = activeAccount();
        when(tokenService.resolveResetToken("reset-token"))
                .thenReturn(Optional.of(new ResetTokenIdentity("admin", 1)));
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        AuthActionResult result = authService.resetPassword(new ResetPasswordRequest("reset-token", "new-password"));

        assertThat(result.success()).isTrue();
        assertThat(account.getPassword()).isEqualTo("new-hash");
        assertThat(account.getTokenVersion()).isEqualTo(2);
        verify(accountRepository).save(account);
    }

    private AuthServiceImpl service(AuthProperties properties) {
        return new AuthServiceImpl(
                accountRepository,
                organizeRepository,
                titleRepository,
                meetingInfoRepository,
                meetingPersonalRepository,
                passwordEncoder,
                tokenService,
                passwordResetNotifier,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static AdAccount activeAccount() {
        AdAccount account = new AdAccount();
        account.setUserName("admin");
        account.setPassword("hashed-password");
        account.setFullName("Quản trị viên");
        account.setEmail("admin@example.com");
        account.setPhone("0909");
        account.setAddress("Hà Nội");
        account.setOrgId("ORG");
        account.setTitleCode("STAFF");
        account.setActive(true);
        account.setMustChangePassword(false);
        account.setTokenVersion(1);
        return account;
    }

    private static MdOrganize activeOrg() {
        MdOrganize org = new MdOrganize();
        org.setId("ORG");
        org.setName("Ban Giám đốc");
        org.setActive(true);
        return org;
    }

    private static MdTitle activeTitle() {
        MdTitle title = new MdTitle();
        title.setCode("STAFF");
        title.setName("Chuyên viên");
        title.setActive(true);
        return title;
    }
}
