package vn.com.d2s.smr.service.ad.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.md.MdTitle;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.md.MdOrganizeRepository;
import vn.com.d2s.smr.repository.md.MdTitleRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.ad.AuthService;
import vn.com.d2s.smr.service.ad.PasswordResetNotifier;
import vn.com.d2s.smr.service.ad.ResetTokenIdentity;
import vn.com.d2s.smr.service.ad.TokenPair;
import vn.com.d2s.smr.service.ad.TokenService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final AdAccountRepository accountRepository;
    private final MdOrganizeRepository organizeRepository;
    private final MdTitleRepository titleRepository;
    private final MeetingInfoRepository meetingInfoRepository;
    private final MeetingPersonalRepository meetingPersonalRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PasswordResetNotifier passwordResetNotifier;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Autowired
    public AuthServiceImpl(
            AdAccountRepository accountRepository,
            MdOrganizeRepository organizeRepository,
            MdTitleRepository titleRepository,
            MeetingInfoRepository meetingInfoRepository,
            MeetingPersonalRepository meetingPersonalRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            PasswordResetNotifier passwordResetNotifier,
            AuthProperties authProperties
    ) {
        this(
                accountRepository,
                organizeRepository,
                titleRepository,
                meetingInfoRepository,
                meetingPersonalRepository,
                passwordEncoder,
                tokenService,
                passwordResetNotifier,
                authProperties,
                Clock.systemUTC()
        );
    }

    AuthServiceImpl(
            AdAccountRepository accountRepository,
            MdOrganizeRepository organizeRepository,
            MdTitleRepository titleRepository,
            MeetingInfoRepository meetingInfoRepository,
            MeetingPersonalRepository meetingPersonalRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            PasswordResetNotifier passwordResetNotifier,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.organizeRepository = organizeRepository;
        this.titleRepository = titleRepository;
        this.meetingInfoRepository = meetingInfoRepository;
        this.meetingPersonalRepository = meetingPersonalRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.passwordResetNotifier = passwordResetNotifier;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuthResultResponse register(RegisterRequest request) {
        if (!authProperties.publicRegistration()) {
            return AuthResultResponse.failure("Hệ thống không kích hoạt tính năng đăng ký công khai.");
        }
        if (authProperties.registrationOrgId().isBlank()
                || authProperties.registrationTitleCode().isBlank()
                || "ADMIN".equalsIgnoreCase(authProperties.registrationTitleCode())) {
            return AuthResultResponse.failure("Cấu hình đăng ký công khai chưa hợp lệ.");
        }

        String userName = request.userName().trim();
        String email = request.email().trim();
        if (accountRepository.existsByUserNameOrEmail(userName, email)) {
            return AuthResultResponse.failure("Tên đăng nhập hoặc email đã tồn tại.");
        }

        boolean validOrganization = organizeRepository.findById(authProperties.registrationOrgId())
                .map(MdOrganize::isActive)
                .orElse(false);
        boolean validTitle = titleRepository.findById(authProperties.registrationTitleCode())
                .map(MdTitle::isActive)
                .orElse(false);
        if (!validOrganization || !validTitle) {
            return AuthResultResponse.failure("Đơn vị hoặc chức danh đăng ký mặc định không hợp lệ.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        AdAccount account = new AdAccount();
        account.setUserName(userName);
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setFullName(request.fullName().trim());
        account.setEmail(email);
        account.setPhone(trimToEmpty(request.phone()));
        account.setAddress("");
        account.setOrgId(authProperties.registrationOrgId());
        account.setTitleCode(authProperties.registrationTitleCode());
        account.setActive(true);
        account.setMustChangePassword(false);
        account.setTokenVersion(1);
        account.setCreateBy(userName);
        account.setCreateDate(now);
        account.setUpdateBy(userName);
        account.setUpdateDate(now);
        accountRepository.save(account);

        return authSuccess(account, "Đăng ký thành công.");
    }

    @Override
    @Transactional
    public AuthResultResponse login(LoginRequest request) {
        String login = request.userName().trim();
        AdAccount account = accountRepository.findFirstByUserNameOrEmail(login, login).orElse(null);

        if (account == null) {
            return AuthResultResponse.failure("Tài khoản không tồn tại.");
        }
        if (!account.isActive()) {
            return AuthResultResponse.failure("Tài khoản đã bị khóa.");
        }
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            return AuthResultResponse.failure("Mật khẩu không chính xác.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        account.setLastLoginAt(now);
        account.setUpdateDate(now);
        accountRepository.save(account);

        TokenPair tokens = tokenService.issueFor(account);
        return new AuthResultResponse(
                true,
                "Đăng nhập thành công.",
                tokens.accessToken(),
                account.getUserName(),
                account.getFullName(),
                account.getEmail(),
                account.isMustChangePassword(),
                tokens.refreshToken()
        );
    }

    @Override
    @Transactional
    public AuthResultResponse refresh(RefreshTokenRequest request) {
        String userName = tokenService.resolveRefreshTokenOwner(request.refreshToken()).orElse(null);
        if (userName == null) {
            return AuthResultResponse.failure("Refresh token không hợp lệ hoặc đã hết hạn.");
        }

        AdAccount account = accountRepository.findById(userName).orElse(null);
        if (account == null || !account.isActive()) {
            return AuthResultResponse.failure("Tài khoản không tồn tại hoặc đã bị khóa.");
        }

        account.setUpdateDate(LocalDateTime.now(clock));
        accountRepository.save(account);

        return authSuccess(account, "Làm mới token thành công.");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfileResponse> getProfile(String userName) {
        return accountRepository.findById(userName)
                .filter(AdAccount::isActive)
                .map(this::toProfile);
    }

    @Override
    @Transactional
    public Optional<UserProfileResponse> updateProfile(String userName, UpdateProfileRequest request) {
        AdAccount account = accountRepository.findById(userName).filter(AdAccount::isActive).orElse(null);
        if (account == null) {
            return Optional.empty();
        }

        String email = request.email().trim();
        if (accountRepository.existsByEmailAndUserNameNot(email, userName)) {
            throw new IllegalArgumentException("Email đã được sử dụng bởi tài khoản khác.");
        }

        account.setFullName(request.fullName().trim());
        account.setEmail(email);
        account.setPhone(trimToEmpty(request.phone()));
        account.setAddress(trimToEmpty(request.address()));
        account.setUpdateBy(userName);
        account.setUpdateDate(LocalDateTime.now(clock));
        accountRepository.save(account);
        return Optional.of(toProfile(account));
    }

    @Override
    @Transactional
    public AuthActionResult changePassword(String userName, ChangePasswordRequest request) {
        AdAccount account = accountRepository.findById(userName).filter(AdAccount::isActive).orElse(null);
        if (account == null) {
            return AuthActionResult.failure("Tài khoản không tồn tại hoặc đã bị khóa.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), account.getPassword())) {
            return AuthActionResult.failure("Mật khẩu hiện tại không chính xác.");
        }
        if (request.newPassword().length() < 8) {
            return AuthActionResult.failure("Mật khẩu mới phải có ít nhất 8 ký tự.");
        }

        account.setPassword(passwordEncoder.encode(request.newPassword()));
        account.setMustChangePassword(false);
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setUpdateBy(userName);
        account.setUpdateDate(LocalDateTime.now(clock));
        accountRepository.save(account);
        return AuthActionResult.success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.");
    }

    @Override
    @Transactional
    public AuthActionResult logout(String userName, LogoutRequest request) {
        AdAccount account = accountRepository.findById(userName).orElse(null);
        if (account == null) {
            return AuthActionResult.failure("Tài khoản không tồn tại.");
        }
        if (!request.logoutAllDevices()) {
            return AuthActionResult.success("Đăng xuất thành công.");
        }

        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setUpdateBy(userName);
        account.setUpdateDate(LocalDateTime.now(clock));
        accountRepository.save(account);
        return AuthActionResult.success("Đăng xuất khỏi tất cả các thiết bị thành công.");
    }

    @Override
    @Transactional(readOnly = true)
    public ForgotPasswordResult forgotPassword(ForgotPasswordRequest request) {
        String genericMessage = "Yêu cầu khôi phục mật khẩu đã được tiếp nhận. "
                + "Vui lòng kiểm tra hộp thư email (nếu email tồn tại trong hệ thống).";
        AdAccount account = accountRepository.findByEmail(request.email().trim())
                .filter(AdAccount::isActive)
                .orElse(null);
        if (account == null) {
            return new ForgotPasswordResult(genericMessage, null);
        }

        String resetToken = tokenService.issueResetToken(account);
        try {
            passwordResetNotifier.send(account, resetToken);
        } catch (RuntimeException ignored) {
            // Giữ response đồng nhất để không làm lộ email có tồn tại hay không.
        }
        return new ForgotPasswordResult(
                genericMessage,
                authProperties.exposeResetToken() ? resetToken : null
        );
    }

    @Override
    @Transactional
    public AuthActionResult resetPassword(ResetPasswordRequest request) {
        ResetTokenIdentity identity = tokenService.resolveResetToken(request.resetToken()).orElse(null);
        if (identity == null) {
            return AuthActionResult.failure("Token khôi phục không hợp lệ hoặc đã hết hạn.");
        }

        AdAccount account = accountRepository.findById(identity.userName()).filter(AdAccount::isActive).orElse(null);
        if (account == null || account.getTokenVersion() != identity.tokenVersion()) {
            return AuthActionResult.failure("Tài khoản không tồn tại hoặc đã bị khóa.");
        }
        if (request.newPassword().length() < 8) {
            return AuthActionResult.failure("Mật khẩu mới phải có ít nhất 8 ký tự.");
        }

        account.setPassword(passwordEncoder.encode(request.newPassword()));
        account.setMustChangePassword(false);
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setUpdateBy(identity.userName());
        account.setUpdateDate(LocalDateTime.now(clock));
        accountRepository.save(account);
        return AuthActionResult.success("Đặt lại mật khẩu thành công. Các phiên truy cập cũ đã được thu hồi.");
    }

    @Override
    @Transactional
    public AuthResultResponse guestSession(GuestSessionRequest request) {
        String meetingId = request.meetingId().trim();
        String displayName = request.displayName().trim();

        MeetingInfo meeting = meetingInfoRepository.findByIdAndDeletedFalse(meetingId).orElse(null);
        if (meeting == null) {
            return AuthResultResponse.failure("Cuộc họp không tồn tại.");
        }
        if (meeting.getStatus() == 3) {
            return AuthResultResponse.failure("Cuộc họp đã kết thúc. Khách không thể tham gia.");
        }

        String guestUserName = "guest_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalDateTime now = LocalDateTime.now(clock);

        MeetingPersonal guestPersonal = new MeetingPersonal();
        guestPersonal.setId(UUID.randomUUID().toString().replace("-", ""));
        guestPersonal.setMeetingId(meeting.getId());
        guestPersonal.setUserName(guestUserName);
        guestPersonal.setFullName(displayName);
        guestPersonal.setPhone("");
        guestPersonal.setEmail("");
        guestPersonal.setAddress("");
        guestPersonal.setOrgId("");
        guestPersonal.setTitleCode("");
        guestPersonal.setReferenceFileId(meeting.getReferenceFileId() != null ? meeting.getReferenceFileId() : "");
        guestPersonal.setType(3);
        guestPersonal.setChairperson(false);
        guestPersonal.setJoined(true);
        guestPersonal.setJoinTime(now);
        guestPersonal.setCreateBy(guestUserName);
        guestPersonal.setCreateDate(now);
        guestPersonal.setUpdateBy(guestUserName);
        guestPersonal.setUpdateDate(now);

        meetingPersonalRepository.save(guestPersonal);

        String token = tokenService.issueForGuest(guestUserName, displayName, meeting.getId());

        return new AuthResultResponse(
                true,
                "Tham gia cuộc họp với vai trò khách thành công.",
                token,
                guestUserName,
                displayName,
                "",
                false,
                null
        );
    }

    private AuthResultResponse authSuccess(AdAccount account, String message) {
        TokenPair tokens = tokenService.issueFor(account);
        return new AuthResultResponse(
                true,
                message,
                tokens.accessToken(),
                account.getUserName(),
                account.getFullName(),
                account.getEmail(),
                account.isMustChangePassword(),
                tokens.refreshToken()
        );
    }

    private UserProfileResponse toProfile(AdAccount account) {
        String organizationName = organizeRepository.findById(account.getOrgId())
                .map(MdOrganize::getName)
                .orElse(null);
        String titleName = titleRepository.findById(account.getTitleCode())
                .map(MdTitle::getName)
                .orElse(null);
        return new UserProfileResponse(
                account.getUserName(),
                account.getFullName(),
                account.getEmail(),
                account.getPhone(),
                account.getAddress(),
                organizationName,
                titleName
        );
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
