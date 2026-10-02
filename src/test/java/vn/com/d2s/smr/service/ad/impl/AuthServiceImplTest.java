package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.com.d2s.smr.config.AuthProperties;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.auth.GuestSessionRequest;
import vn.com.d2s.smr.dto.ad.auth.LoginRequest;
import vn.com.d2s.smr.dto.ad.auth.RefreshTokenRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.ad.PasswordResetNotifier;
import vn.com.d2s.smr.service.ad.TokenPair;
import vn.com.d2s.smr.service.ad.TokenService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-30T01:00:00Z");

    @Mock
    private AdAccountRepository accountRepository;

    @Mock
    private MdOrganizeRepository organizeRepository;

    @Mock
    private MdTitleRepository titleRepository;

    @Mock
    private MeetingInfoRepository meetingInfoRepository;

    @Mock
    private MeetingPersonalRepository meetingPersonalRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordResetNotifier passwordResetNotifier;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                accountRepository,
                organizeRepository,
                titleRepository,
                meetingInfoRepository,
                meetingPersonalRepository,
                passwordEncoder,
                tokenService,
                passwordResetNotifier,
                new AuthProperties(true, "ORG", "STAFF", false),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void loginAcceptsUsernameAndReturnsTokenPair() {
        AdAccount account = activeAccount();
        when(accountRepository.findFirstByUserNameOrEmail("admin", "admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(tokenService.issueFor(account)).thenReturn(new TokenPair("access-token", "refresh-token"));

        AuthResultResponse result = authService.login(new LoginRequest("  admin  ", "correct-password"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Đăng nhập thành công.");
        assertThat(result.token()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.userName()).isEqualTo("admin");
        assertThat(account.getLastLoginAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(account.getUpdateDate()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(accountRepository).save(account);
    }

    @Test
    void loginAcceptsEmail() {
        AdAccount account = activeAccount();
        when(accountRepository.findFirstByUserNameOrEmail("admin@example.com", "admin@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(tokenService.issueFor(account)).thenReturn(new TokenPair("access-token", "refresh-token"));

        AuthResultResponse result = authService.login(new LoginRequest("admin@example.com", "correct-password"));

        assertThat(result.success()).isTrue();
    }

    @Test
    void loginRejectsUnknownAccountWithoutIssuingToken() {
        when(accountRepository.findFirstByUserNameOrEmail("missing", "missing")).thenReturn(Optional.empty());

        AuthResultResponse result = authService.login(new LoginRequest("missing", "password"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Tài khoản không tồn tại.");
        verify(tokenService, never()).issueFor(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginRejectsInvalidPasswordWithoutUpdatingAccount() {
        AdAccount account = activeAccount();
        when(accountRepository.findFirstByUserNameOrEmail("admin", "admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        AuthResultResponse result = authService.login(new LoginRequest("admin", "wrong-password"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Mật khẩu không chính xác.");
        verify(accountRepository, never()).save(account);
        verify(tokenService, never()).issueFor(account);
    }

    @Test
    void refreshRotatesTokenPairForActiveAccount() {
        AdAccount account = activeAccount();
        when(tokenService.resolveRefreshTokenOwner("old-refresh-token")).thenReturn(Optional.of("admin"));
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        when(tokenService.issueFor(account)).thenReturn(new TokenPair("new-access-token", "new-refresh-token"));

        AuthResultResponse result = authService.refresh(new RefreshTokenRequest("old-refresh-token"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Làm mới token thành công.");
        assertThat(result.token()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(result.userName()).isEqualTo("admin");
        assertThat(account.getUpdateDate()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(accountRepository).save(account);
    }

    @Test
    void refreshRejectsInvalidTokenWithoutLoadingAccount() {
        when(tokenService.resolveRefreshTokenOwner("invalid-token")).thenReturn(Optional.empty());

        AuthResultResponse result = authService.refresh(new RefreshTokenRequest("invalid-token"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Refresh token không hợp lệ hoặc đã hết hạn.");
        verify(accountRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refreshRejectsLockedAccountWithoutIssuingTokens() {
        AdAccount account = activeAccount();
        account.setActive(false);
        when(tokenService.resolveRefreshTokenOwner("refresh-token")).thenReturn(Optional.of("admin"));
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));

        AuthResultResponse result = authService.refresh(new RefreshTokenRequest("refresh-token"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Tài khoản không tồn tại hoặc đã bị khóa.");
        verify(accountRepository, never()).save(account);
        verify(tokenService, never()).issueFor(account);
    }

    @Test
    void guestSessionCreatesGuestParticipantAndReturnsJwtToken() {
        MeetingInfo meeting = new MeetingInfo();
        meeting.setId("m1");
        meeting.setStatus(1);
        meeting.setReferenceFileId("ref1");

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(tokenService.issueForGuest(any(), any(), any())).thenReturn("guest-jwt-token");

        AuthResultResponse result = authService.guestSession(new GuestSessionRequest("m1", "Khách Mời A"));

        assertThat(result.success()).isTrue();
        assertThat(result.token()).isEqualTo("guest-jwt-token");
        assertThat(result.fullName()).isEqualTo("Khách Mời A");
        verify(meetingPersonalRepository).save(any(MeetingPersonal.class));
    }

    private static AdAccount activeAccount() {
        AdAccount account = new AdAccount();
        account.setUserName("admin");
        account.setPassword("hashed-password");
        account.setFullName("Quản trị viên");
        account.setEmail("admin@example.com");
        account.setActive(true);
        account.setMustChangePassword(false);
        account.setTokenVersion(1);
        return account;
    }
}
