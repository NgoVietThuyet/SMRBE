package vn.com.d2s.smr.service.ad.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.config.AdminAccess;
import vn.com.d2s.smr.config.FaceProperties;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.face.FaceActionResult;
import vn.com.d2s.smr.dto.ad.face.FaceAdminEnrollRequest;
import vn.com.d2s.smr.dto.ad.face.FaceChallengeResponse;
import vn.com.d2s.smr.dto.ad.face.FaceEnrollRequest;
import vn.com.d2s.smr.dto.ad.face.FaceJoinRequest;
import vn.com.d2s.smr.dto.ad.face.FaceLoginRequest;
import vn.com.d2s.smr.dto.ad.face.FaceStatusResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.ad.AdFaceTemplate;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.ad.AdFaceTemplateRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.ad.AuthService;
import vn.com.d2s.smr.service.ad.FaceAuthService;
import vn.com.d2s.smr.service.ad.face.FaceChallengeStore;
import vn.com.d2s.smr.service.ad.face.FaceEngineClient;
import vn.com.d2s.smr.service.ad.face.FaceEngineClient.FaceAnalysis;
import vn.com.d2s.smr.service.ad.face.FaceEngineException;
import vn.com.d2s.smr.service.ad.face.FaceMath;
import vn.com.d2s.smr.service.ad.face.FaceRateLimiter;
import vn.com.d2s.smr.service.ad.face.FaceTemplateCrypto;
import vn.com.d2s.smr.service.mt.impl.RoomAdmission;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Quyết định nhận dạng nằm ở đây (dịch vụ phụ chỉ tính đặc trưng): kiểm tra mã thử thách, mặt thật, so cosine,
 * giới hạn thử sai. Tài khoản quản trị (role SUPER_ADMIN) không dùng được. Mọi lỗi xác thực trả thông báo chung.
 */
@Service
public class FaceAuthServiceImpl implements FaceAuthService {

    private static final Logger log = LoggerFactory.getLogger(FaceAuthServiceImpl.class);
    static final String GENERIC = "Không xác thực được khuôn mặt. Hãy thử lại hoặc đăng nhập bằng mật khẩu.";
    private static final int MAX_FRAME_CHARS = 1_500_000;
    private static final int MIN_ENROLL_FRAMES = 3;

    /** Ảnh không đạt (chất lượng, số mặt, mặt giả...). counted=true: coi là một lần thử sai. */
    private static final class Reject extends RuntimeException {
        final boolean counted;

        Reject(String message, boolean counted) {
            super(message, null, false, false);
            this.counted = counted;
        }
    }

    private record Probe(float[] embedding, double quality, String model, int dim) {
    }

    private final AdAccountRepository accountRepository;
    private final AdFaceTemplateRepository templateRepository;
    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final FaceEngineClient engine;
    private final FaceTemplateCrypto crypto;
    private final FaceChallengeStore challenges;
    private final FaceRateLimiter limiter;
    private final FaceProperties props;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final Clock clock;

    @Autowired
    public FaceAuthServiceImpl(AdAccountRepository accountRepository, AdFaceTemplateRepository templateRepository,
                               MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                               FaceEngineClient engine, FaceTemplateCrypto crypto, FaceChallengeStore challenges,
                               FaceRateLimiter limiter, FaceProperties props, PasswordEncoder passwordEncoder,
                               AuthService authService) {
        this(accountRepository, templateRepository, meetingRepository, personalRepository, engine, crypto, challenges,
                limiter, props, passwordEncoder, authService, Clock.systemUTC());
    }

    FaceAuthServiceImpl(AdAccountRepository accountRepository, AdFaceTemplateRepository templateRepository,
                        MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                        FaceEngineClient engine, FaceTemplateCrypto crypto, FaceChallengeStore challenges,
                        FaceRateLimiter limiter, FaceProperties props, PasswordEncoder passwordEncoder,
                        AuthService authService, Clock clock) {
        this.accountRepository = accountRepository;
        this.templateRepository = templateRepository;
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.engine = engine;
        this.crypto = crypto;
        this.challenges = challenges;
        this.limiter = limiter;
        this.props = props;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.clock = clock;
    }

    private boolean ready() {
        return props.enabled() && crypto.isConfigured();
    }

    // ===================== Thử thách =====================

    @Override
    public FaceChallengeResponse challenge(String ip) {
        if (!ready()) return new FaceChallengeResponse(false, null, null, 0);
        FaceChallengeStore.Challenge c = challenges.issue(ip);
        return new FaceChallengeResponse(true, c.id(), c.gesture(), c.ttlSeconds());
    }

    // ===================== Đăng nhập 1:1 =====================

    @Override
    @Transactional
    public AuthResultResponse login(FaceLoginRequest request, String ip) {
        if (!ready()) return AuthResultResponse.failure("Tính năng đăng nhập bằng khuôn mặt chưa được bật.");
        String login = request.userName().trim();
        String ipKey = "ip:" + ip;
        String userKey = "user:" + login.toLowerCase(Locale.ROOT);
        long locked = limiter.lockedMinutes(ipKey, userKey);
        if (locked > 0) {
            return AuthResultResponse.failure("Thử quá nhiều lần. Vui lòng thử lại sau " + locked + " phút hoặc dùng mật khẩu.");
        }
        if (!challenges.consume(request.challengeId(), ip)) {
            return AuthResultResponse.failure("Phiên quét mặt đã hết hạn. Hãy quét lại.");
        }
        Probe probe;
        try {
            probe = analyze(request.frames(), props.minFrames(), props.maxFrames());
        } catch (Reject r) {
            if (r.counted) limiter.fail(ipKey, userKey);
            return AuthResultResponse.failure(r.counted ? GENERIC : r.getMessage());
        } catch (FaceEngineException e) {
            return AuthResultResponse.failure("Dịch vụ nhận dạng khuôn mặt tạm thời không khả dụng. Hãy dùng mật khẩu.");
        }

        AdAccount account = accountRepository.findFirstByUserNameOrEmail(login, login).orElse(null);
        if (account != null && eligible(account)) {
            double best = bestSimilarity(probe.embedding(), templateRepository.findByUserNameIgnoreCaseAndActiveTrue(account.getUserName()));
            if (best >= props.threshold()) {
                limiter.success(ipKey, userKey);
                return authService.loginVerified(account);
            }
        }
        limiter.fail(ipKey, userKey);
        return AuthResultResponse.failure(GENERIC);
    }

    // ===================== Vào họp: 1:N trong thành viên cuộc họp =====================

    @Override
    @Transactional
    public AuthResultResponse join(FaceJoinRequest request, String ip) {
        if (!ready()) return AuthResultResponse.failure("Tính năng đăng nhập bằng khuôn mặt chưa được bật.");
        String ipKey = "ip:" + ip;
        long locked = limiter.lockedMinutes(ipKey);
        if (locked > 0) {
            return AuthResultResponse.failure("Thử quá nhiều lần. Vui lòng thử lại sau " + locked + " phút.");
        }
        if (!challenges.consume(request.challengeId(), ip)) {
            return AuthResultResponse.failure("Phiên quét mặt đã hết hạn. Hãy quét lại.");
        }
        Probe probe;
        try {
            probe = analyze(request.frames(), props.minFrames(), props.maxFrames());
        } catch (Reject r) {
            if (r.counted) limiter.fail(ipKey);
            return AuthResultResponse.failure(r.counted ? GENERIC : r.getMessage());
        } catch (FaceEngineException e) {
            return AuthResultResponse.failure("Dịch vụ nhận dạng khuôn mặt tạm thời không khả dụng.");
        }

        MeetingInfo meeting = meetingRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse(request.roomCode().trim()).orElse(null);
        if (meeting == null || (meeting.getStatus() != 1 && meeting.getStatus() != 2)) {
            limiter.fail(ipKey);
            return AuthResultResponse.failure(GENERIC);
        }
        List<String> memberNames = personalRepository.findByMeetingId(meeting.getId()).stream()
                .map(MeetingPersonal::getUserName).filter(u -> !RoomAdmission.isGuest(u)).distinct().toList();
        Map<String, AdAccount> accounts = new HashMap<>();
        for (AdAccount a : accountRepository.findAllById(memberNames)) {
            if (eligible(a)) accounts.put(a.getUserName().toLowerCase(Locale.ROOT), a);
        }
        List<AdFaceTemplate> templates = accounts.isEmpty() ? List.of()
                : templateRepository.findByUserNameInAndActiveTrue(accounts.values().stream().map(AdAccount::getUserName).toList());

        Map<String, List<AdFaceTemplate>> byUser = new HashMap<>();
        for (AdFaceTemplate t : templates) byUser.computeIfAbsent(t.getUserName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(t);
        record Scored(String user, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<String, List<AdFaceTemplate>> e : byUser.entrySet()) {
            scored.add(new Scored(e.getKey(), bestSimilarity(probe.embedding(), e.getValue())));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        if (!scored.isEmpty() && scored.get(0).score() >= props.threshold()
                && (scored.size() == 1 || scored.get(0).score() - scored.get(1).score() >= props.margin())) {
            limiter.success(ipKey);
            return authService.loginVerified(accounts.get(scored.get(0).user()));
        }
        limiter.fail(ipKey);
        return AuthResultResponse.failure(GENERIC);
    }

    // ===================== Đăng ký =====================

    @Override
    @Transactional
    public FaceActionResult enroll(String userName, FaceEnrollRequest request) {
        if (!ready()) return FaceActionResult.failure("Tính năng đăng nhập bằng khuôn mặt chưa được bật.");
        AdAccount account = accountRepository.findById(userName).orElse(null);
        if (account == null || !account.isActive()) return FaceActionResult.failure("Tài khoản không hợp lệ.");
        if (!eligible(account)) {
            return FaceActionResult.failure("Tài khoản quản trị không dùng đăng nhập bằng khuôn mặt.");
        }
        if (!request.consent()) {
            return FaceActionResult.failure("Bạn cần đồng ý cho hệ thống xử lý dữ liệu khuôn mặt (dữ liệu sinh trắc học).");
        }
        String key = "enroll:" + userName.toLowerCase(Locale.ROOT);
        long locked = limiter.lockedMinutes(key);
        if (locked > 0) return FaceActionResult.failure("Thử quá nhiều lần. Vui lòng thử lại sau " + locked + " phút.");
        if (!passwordEncoder.matches(request.password(), account.getPassword())) {
            limiter.fail(key);
            return FaceActionResult.failure("Mật khẩu không chính xác.");
        }
        limiter.success(key);
        return storeEnrollment(account, request.frames());
    }

    @Override
    @Transactional
    public FaceActionResult enrollByAdmin(String targetUserName, FaceAdminEnrollRequest request) {
        if (!ready()) return FaceActionResult.failure("Tính năng đăng nhập bằng khuôn mặt chưa được bật.");
        AdAccount account = accountRepository.findById(targetUserName).orElse(null);
        if (account == null || !account.isActive()) return FaceActionResult.failure("Tài khoản không hợp lệ.");
        if (!eligible(account)) {
            return FaceActionResult.failure("Tài khoản quản trị không dùng đăng nhập bằng khuôn mặt.");
        }
        if (!request.consent()) {
            return FaceActionResult.failure("Cần người dùng đồng ý cho hệ thống xử lý dữ liệu khuôn mặt (dữ liệu sinh trắc học).");
        }
        return storeEnrollment(account, request.frames());
    }

    /** Phân tích ảnh, kiểm tra cùng một người + không trùng tài khoản khác rồi lưu mẫu mã hóa (thay mẫu cũ). */
    private FaceActionResult storeEnrollment(AdAccount account, List<String> frames) {
        String userName = account.getUserName();
        List<float[]> embeddings = new ArrayList<>();
        double quality = 1;
        String model = "";
        int dim = 0;
        try {
            if (frames.size() < MIN_ENROLL_FRAMES || frames.size() > props.maxEnrollFrames()) {
                return FaceActionResult.failure("Cần từ " + MIN_ENROLL_FRAMES + " đến " + props.maxEnrollFrames() + " ảnh khuôn mặt.");
            }
            for (String f : frames) {
                Probe p = analyze(List.of(f), 1, 1);
                embeddings.add(p.embedding());
                quality = Math.min(quality, p.quality());
                model = p.model();
                dim = p.dim();
            }
        } catch (Reject r) {
            return FaceActionResult.failure(r.getMessage());
        } catch (FaceEngineException e) {
            return FaceActionResult.failure("Dịch vụ nhận dạng khuôn mặt tạm thời không khả dụng.");
        }
        for (int i = 1; i < embeddings.size(); i++) {
            if (FaceMath.similarity(embeddings.get(0), embeddings.get(i)) < props.sampleAgreement()) {
                return FaceActionResult.failure("Các ảnh không cùng một người. Hãy chụp lại, chỉ một mình bạn trong khung hình.");
            }
        }
        float[] mean = FaceMath.mean(embeddings);
        // Chống đăng ký mặt người khác: không được giống mẫu của tài khoản khác.
        for (AdFaceTemplate t : templateRepository.findByActiveTrue()) {
            if (t.getUserName().equalsIgnoreCase(userName)) continue;
            if (similarityTo(mean, t) >= props.threshold()) {
                return FaceActionResult.failure("Khuôn mặt này đã được đăng ký cho một tài khoản khác.");
            }
        }

        templateRepository.deleteByUserNameIgnoreCase(userName);
        LocalDateTime now = LocalDateTime.now(clock);
        for (float[] e : embeddings) {
            AdFaceTemplate t = new AdFaceTemplate();
            t.setId(UUID.randomUUID().toString().replace("-", ""));
            t.setUserName(account.getUserName());
            t.setEmbedding(crypto.encrypt(e, account.getUserName()));
            t.setModel(model);
            t.setDim(dim);
            t.setQuality(quality);
            t.setCreatedAt(now);
            t.setActive(true);
            templateRepository.save(t);
        }
        return FaceActionResult.ok("Đã đăng ký khuôn mặt.");
    }

    @Override
    @Transactional(readOnly = true)
    public FaceStatusResponse status(String userName) {
        AdAccount account = accountRepository.findById(userName).orElse(null);
        boolean allowed = account != null && eligible(account);
        int samples = templateRepository.findByUserNameIgnoreCaseAndActiveTrue(userName).size();
        return new FaceStatusResponse(ready(), allowed, samples > 0, samples);
    }

    @Override
    @Transactional
    public FaceActionResult remove(String userName) {
        templateRepository.deleteByUserNameIgnoreCase(userName);
        return FaceActionResult.ok("Đã xóa dữ liệu khuôn mặt.");
    }

    // ===================== Tiện ích =====================

    /** Tài khoản được dùng khuôn mặt: đang hoạt động và không phải tài khoản quản trị (SUPER_ADMIN). */
    private static boolean eligible(AdAccount a) {
        return a.isActive() && !AdminAccess.hasSuperAdminRole(a);
    }

    private double bestSimilarity(float[] probe, List<AdFaceTemplate> templates) {
        double best = -1;
        for (AdFaceTemplate t : templates) best = Math.max(best, similarityTo(probe, t));
        return best;
    }

    private double similarityTo(float[] probe, AdFaceTemplate t) {
        try {
            return FaceMath.similarity(probe, FaceMath.normalize(crypto.decrypt(t.getEmbedding(), t.getUserName())));
        } catch (IllegalStateException e) {
            log.warn("Bỏ qua mẫu khuôn mặt {} không giải mã được: {}", t.getId(), e.getMessage());
            return -1;
        }
    }

    /** Phân tích các khung hình: đúng một mặt, mặt thật, đủ nét, các khung cùng một người → đặc trưng trung bình. */
    private Probe analyze(List<String> frames, int min, int max) {
        if (frames == null || frames.size() < min || frames.size() > max) {
            throw new Reject("Số ảnh gửi lên không hợp lệ.", false);
        }
        List<float[]> embeddings = new ArrayList<>();
        double quality = 1;
        String model = "";
        int dim = 0;
        for (String f : frames) {
            if (f == null || f.isBlank() || f.length() > MAX_FRAME_CHARS) {
                throw new Reject("Ảnh không hợp lệ hoặc quá lớn.", false);
            }
            FaceAnalysis a = engine.analyze(f);
            if (a.faces() != 1 || a.embedding() == null || a.embedding().length == 0) {
                throw new Reject("Cần đúng một khuôn mặt rõ trong khung hình.", false);
            }
            if (a.quality() < props.minQuality()) {
                throw new Reject("Ảnh chưa đủ rõ. Hãy quét lại ở nơi đủ sáng, nhìn thẳng vào camera.", false);
            }
            // Không có kết quả "mặt thật" (dịch vụ không hỗ trợ) cũng bị từ chối: fail-closed.
            if (!Boolean.TRUE.equals(a.real()) || (a.antispoofScore() != null && a.antispoofScore() < props.minLiveness())) {
                throw new Reject("Không xác nhận được khuôn mặt thật.", true);
            }
            embeddings.add(FaceMath.normalize(a.embedding()));
            quality = Math.min(quality, a.quality());
            model = a.model();
            dim = a.dim() > 0 ? a.dim() : a.embedding().length;
        }
        for (int i = 1; i < embeddings.size(); i++) {
            if (FaceMath.similarity(embeddings.get(0), embeddings.get(i)) < props.sampleAgreement()) {
                throw new Reject("Các khung hình không cùng một người.", true);
            }
        }
        return new Probe(FaceMath.mean(embeddings), quality, model, dim);
    }
}
