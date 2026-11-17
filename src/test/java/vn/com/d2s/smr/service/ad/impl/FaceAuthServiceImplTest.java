package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.com.d2s.smr.config.FaceProperties;
import vn.com.d2s.smr.dto.ad.auth.AuthResultResponse;
import vn.com.d2s.smr.dto.ad.face.FaceActionResult;
import vn.com.d2s.smr.dto.ad.face.FaceAdminEnrollRequest;
import vn.com.d2s.smr.dto.ad.face.FaceEnrollRequest;
import vn.com.d2s.smr.dto.ad.face.FaceJoinRequest;
import vn.com.d2s.smr.dto.ad.face.FaceLoginRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.ad.AdFaceTemplate;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.ad.AdFaceTemplateRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.ad.AuthService;
import vn.com.d2s.smr.service.ad.face.FaceChallengeStore;
import vn.com.d2s.smr.service.ad.face.FaceEngineClient;
import vn.com.d2s.smr.service.ad.face.FaceEngineException;
import vn.com.d2s.smr.service.ad.face.FaceMath;
import vn.com.d2s.smr.service.ad.face.FaceRateLimiter;
import vn.com.d2s.smr.service.ad.face.FaceTemplateCrypto;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaceAuthServiceImplTest {

    private static final String IP = "1.1.1.1";
    /** Đặc trưng 4 chiều cho dễ tính: mỗi người một trục. */
    private static final Map<String, float[]> FACES = Map.of(
            "alice", new float[]{1f, 0f, 0f, 0f},
            "alice2", new float[]{0.97f, 0.1f, 0f, 0f},
            "bob", new float[]{0f, 1f, 0f, 0f},
            "mix", new float[]{0.7f, 0.7f, 0f, 0f},   // nằm giữa alice và bob
            "carol", new float[]{0f, 0f, 1f, 0f});

    @Mock private AdAccountRepository accounts;
    @Mock private AdFaceTemplateRepository templates;
    @Mock private MeetingInfoRepository meetings;
    @Mock private MeetingPersonalRepository personals;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthService authService;

    private final List<AdFaceTemplate> store = new ArrayList<>();
    private final Map<String, AdAccount> byName = new java.util.HashMap<>();
    private final List<String> engineCalls = new ArrayList<>();
    private boolean engineDown;
    private boolean spoof;

    private FaceProperties props;
    private FaceTemplateCrypto crypto;
    private FaceChallengeStore challenges;
    private FaceRateLimiter limiter;
    private FaceAuthServiceImpl service;

    private AdAccount account(String user, String roles) {
        AdAccount a = new AdAccount();
        a.setUserName(user);
        a.setFullName(user.toUpperCase());
        a.setPassword("hash");
        a.setRoleCodes(roles);
        byName.put(user, a);
        return a;
    }

    @BeforeEach
    void setUp() {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, (byte) 5);
        props = new FaceProperties(true, "http://x", "k", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Base64.getEncoder().encodeToString(k));
        crypto = new FaceTemplateCrypto(props);
        challenges = new FaceChallengeStore(props);
        limiter = new FaceRateLimiter(props);

        FaceEngineClient engine = image -> {
            engineCalls.add(image);
            if (engineDown) throw new FaceEngineException("down");
            float[] v = FACES.get(image);
            if (v == null) return new FaceEngineClient.FaceAnalysis(0, null, null, null, 0, "", 0);
            return new FaceEngineClient.FaceAnalysis(1, v, !spoof, spoof ? 0.1 : 0.99, 0.9, "ArcFace", 4);
        };
        service = new FaceAuthServiceImpl(accounts, templates, meetings, personals, engine, crypto, challenges, limiter,
                props, passwordEncoder, authService, java.time.Clock.systemUTC());

        when(accounts.findFirstByUserNameOrEmail(anyString(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(byName.get((String) i.getArgument(0))));
        when(accounts.findById(anyString())).thenAnswer(i -> Optional.ofNullable(byName.get((String) i.getArgument(0))));
        when(accounts.findAllById(any())).thenAnswer(i -> {
            List<AdAccount> out = new ArrayList<>();
            for (Object n : (Iterable<?>) i.getArgument(0)) if (byName.containsKey(n)) out.add(byName.get(n));
            return out;
        });
        when(templates.findByUserNameIgnoreCaseAndActiveTrue(anyString())).thenAnswer(i -> store.stream()
                .filter(t -> t.getUserName().equalsIgnoreCase(i.getArgument(0))).toList());
        when(templates.findByUserNameInAndActiveTrue(anyCollection())).thenAnswer(i -> {
            java.util.Collection<String> names = i.getArgument(0);
            return store.stream().filter(t -> names.contains(t.getUserName())).toList();
        });
        when(templates.findByActiveTrue()).thenAnswer(i -> List.copyOf(store));
        when(templates.save(any(AdFaceTemplate.class))).thenAnswer(i -> {
            store.add(i.getArgument(0));
            return i.getArgument(0);
        });
        org.mockito.Mockito.doAnswer(i -> {
            store.removeIf(t -> t.getUserName().equalsIgnoreCase(i.getArgument(0)));
            return null;
        }).when(templates).deleteByUserNameIgnoreCase(anyString());
        when(authService.loginVerified(any(AdAccount.class))).thenAnswer(i -> new AuthResultResponse(
                true, "ok", "tok-" + ((AdAccount) i.getArgument(0)).getUserName(), ((AdAccount) i.getArgument(0)).getUserName(), "", "", false, "rt"));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
    }

    /** Lưu sẵn mẫu đã đăng ký cho một người. */
    private void enrolled(String user, String... faces) {
        for (String f : faces) {
            AdFaceTemplate t = new AdFaceTemplate();
            t.setId(user + f);
            t.setUserName(user);
            t.setEmbedding(crypto.encrypt(FaceMath.normalize(FACES.get(f)), user));
            t.setModel("ArcFace");
            t.setDim(4);
            t.setActive(true);
            store.add(t);
        }
    }

    private FaceLoginRequest login(String user, String... frames) {
        return new FaceLoginRequest(user, challenges.issue(IP).id(), List.of(frames));
    }

    @Test
    void loginSucceedsWhenFaceMatchesOwnTemplate() {
        account("alice", "[]");
        enrolled("alice", "alice");

        AuthResultResponse r = service.login(login("alice", "alice2", "alice2"), IP);

        assertThat(r.success()).isTrue();
        assertThat(r.token()).isEqualTo("tok-alice");
    }

    @Test
    void loginFailsWithGenericMessageForAnotherPersonOrUnknownAccountOrNoTemplate() {
        account("alice", "[]");
        account("bob", "[]");
        enrolled("alice", "alice");

        AuthResultResponse otherFace = service.login(login("alice", "bob", "bob"), IP);
        AuthResultResponse unknown = service.login(login("ghost", "alice", "alice"), IP);
        AuthResultResponse noTemplate = service.login(login("bob", "bob", "bob"), IP);

        assertThat(otherFace.success()).isFalse();
        assertThat(otherFace.message()).isEqualTo(FaceAuthServiceImpl.GENERIC);
        assertThat(unknown.message()).isEqualTo(FaceAuthServiceImpl.GENERIC);
        assertThat(noTemplate.message()).isEqualTo(FaceAuthServiceImpl.GENERIC);
    }

    @Test
    void spoofIsRejectedAndRepeatedFailuresLockTheAccount() {
        account("alice", "[]");
        enrolled("alice", "alice");
        spoof = true;

        for (int i = 0; i < 5; i++) {
            assertThat(service.login(login("alice", "alice", "alice"), IP).message()).isEqualTo(FaceAuthServiceImpl.GENERIC);
        }
        spoof = false;
        AuthResultResponse locked = service.login(login("alice", "alice", "alice"), IP);

        assertThat(locked.success()).isFalse();
        assertThat(locked.message()).contains("quá nhiều lần");
    }

    @Test
    void challengeWorksOnlyOnceAndOnlyFromSameIp() {
        account("alice", "[]");
        enrolled("alice", "alice");
        FaceLoginRequest req = login("alice", "alice", "alice");

        assertThat(service.login(req, IP).success()).isTrue();
        assertThat(service.login(req, IP).message()).contains("hết hạn");
        assertThat(service.login(new FaceLoginRequest("alice", challenges.issue(IP).id(), List.of("alice", "alice")), "9.9.9.9")
                .message()).contains("hết hạn");
    }

    @Test
    void superAdminCannotUseFaceButAdminRightHolderCan() {
        account("root", "[\"SUPER_ADMIN\"]");
        enrolled("root", "alice");
        account("rights", "[]"); // chỉ được gán quyền Admin, không phải role SUPER_ADMIN
        enrolled("rights", "bob");

        assertThat(service.login(login("root", "alice", "alice"), IP).success()).isFalse();
        assertThat(service.login(login("rights", "bob", "bob"), IP).success()).isTrue();
        assertThat(service.status("root").allowed()).isFalse();
        assertThat(service.status("rights").allowed()).isTrue();
    }

    @Test
    void engineOutageIsNotCountedAsAFailedAttempt() {
        account("alice", "[]");
        enrolled("alice", "alice");
        engineDown = true;
        for (int i = 0; i < 8; i++) {
            assertThat(service.login(login("alice", "alice", "alice"), IP).message()).contains("tạm thời");
        }
        engineDown = false;

        assertThat(service.login(login("alice", "alice", "alice"), IP).success()).isTrue();
    }

    @Test
    void enrollStoresEncryptedTemplatesAndReplacesOldOnes() {
        account("alice", "[]");

        FaceActionResult r = service.enroll("alice", new FaceEnrollRequest("pw", true, List.of("alice", "alice2", "alice")));

        assertThat(r.success()).isTrue();
        assertThat(store).hasSize(3);
        assertThat(store.get(0).getEmbedding()).hasSize(12 + 16 + 16);
        assertThat(service.status("alice").enrolled()).isTrue();

        service.enroll("alice", new FaceEnrollRequest("pw", true, List.of("alice", "alice", "alice")));
        assertThat(store).hasSize(3);
        assertThat(service.remove("alice").success()).isTrue();
        assertThat(store).isEmpty();
    }

    @Test
    void enrollRejectsWrongPasswordMissingConsentSuperAdminMixedPeopleAndDuplicateFace() {
        account("alice", "[]");
        account("bob", "[]");
        account("root", "[\"SUPER_ADMIN\"]");
        enrolled("bob", "alice"); // mặt của alice đã được đăng ký cho bob

        assertThat(service.enroll("alice", new FaceEnrollRequest("sai", true, List.of("alice", "alice", "alice"))).message())
                .contains("Mật khẩu");
        assertThat(service.enroll("alice", new FaceEnrollRequest("pw", false, List.of("alice", "alice", "alice"))).message())
                .contains("đồng ý");
        assertThat(service.enroll("root", new FaceEnrollRequest("pw", true, List.of("alice", "alice", "alice"))).message())
                .contains("quản trị");
        assertThat(service.enroll("alice", new FaceEnrollRequest("pw", true, List.of("alice", "carol", "alice"))).message())
                .contains("không cùng một người");
        assertThat(service.enroll("alice", new FaceEnrollRequest("pw", true, List.of("alice", "alice", "alice"))).message())
                .contains("tài khoản khác");
        assertThat(service.enroll("alice", new FaceEnrollRequest("pw", true, List.of("alice"))).message()).contains("Cần từ 3");
        assertThat(store).hasSize(1); // chỉ còn mẫu cũ của bob
    }

    @Test
    void adminCanEnrollFaceForANewAccountWithoutItsPasswordButNeedsConsentAndNotSuperAdmin() {
        account("newbie", "[]");
        account("root", "[\"SUPER_ADMIN\"]");
        enrolled("bob", "bob"); // mặt của bob đã đăng ký cho tài khoản khác
        account("bob", "[]");

        assertThat(service.enrollByAdmin("newbie", new FaceAdminEnrollRequest(false, List.of("alice", "alice", "alice"))).message())
                .contains("đồng ý");
        assertThat(service.enrollByAdmin("root", new FaceAdminEnrollRequest(true, List.of("alice", "alice", "alice"))).message())
                .contains("quản trị");
        assertThat(service.enrollByAdmin("ghost", new FaceAdminEnrollRequest(true, List.of("alice", "alice", "alice"))).success()).isFalse();
        assertThat(service.enrollByAdmin("newbie", new FaceAdminEnrollRequest(true, List.of("bob", "bob", "bob"))).message())
                .contains("tài khoản khác");

        FaceActionResult ok = service.enrollByAdmin("newbie", new FaceAdminEnrollRequest(true, List.of("alice", "alice2", "alice")));

        assertThat(ok.success()).isTrue();
        assertThat(service.status("newbie").enrolled()).isTrue();
        // Sau đó đăng nhập bằng mặt được.
        assertThat(service.login(login("newbie", "alice", "alice"), IP).success()).isTrue();
    }

    private MeetingInfo meetingWith(int status, String... members) {
        MeetingInfo m = new MeetingInfo();
        m.setId("m1");
        m.setRoomCode("ROOM");
        m.setStatus(status);
        when(meetings.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("ROOM")).thenReturn(Optional.of(m));
        List<MeetingPersonal> list = new ArrayList<>();
        for (String u : members) {
            MeetingPersonal p = new MeetingPersonal();
            p.setUserName(u);
            list.add(p);
        }
        when(personals.findByMeetingId("m1")).thenReturn(list);
        return m;
    }

    private FaceJoinRequest join(String... frames) {
        return new FaceJoinRequest("ROOM", challenges.issue(IP).id(), List.of(frames));
    }

    @Test
    void joinRecognizesAMemberOnlyAmongMeetingMembers() {
        account("alice", "[]");
        account("bob", "[]");
        account("carol", "[]");
        enrolled("alice", "alice");
        enrolled("bob", "bob");
        enrolled("carol", "carol");
        meetingWith(2, "alice", "bob", "guest_ab12cd34");

        AuthResultResponse a = service.join(join("alice2", "alice2"), IP);
        AuthResultResponse outsider = service.join(join("carol", "carol"), IP); // carol có mặt nhưng không là thành viên

        assertThat(a.success()).isTrue();
        assertThat(a.token()).isEqualTo("tok-alice");
        assertThat(outsider.success()).isFalse();
    }

    @Test
    void joinRejectsAmbiguousMatchEndedMeetingAndSuperAdmin() {
        account("alice", "[]");
        account("bob", "[]");
        account("root", "[\"SUPER_ADMIN\"]");
        enrolled("alice", "alice");
        enrolled("bob", "bob");
        enrolled("root", "carol");
        meetingWith(2, "alice", "bob", "root");

        assertThat(service.join(join("mix", "mix"), IP).success()).isFalse();       // giữa alice và bob → không đủ chênh lệch
        assertThat(service.join(join("carol", "carol"), IP).success()).isFalse();   // chỉ khớp tài khoản quản trị
        meetingWith(3, "alice", "bob");
        assertThat(service.join(join("alice", "alice"), IP).success()).isFalse();   // cuộc họp đã kết thúc
    }

    @Test
    void disabledOrWithoutKeyEverythingIsRefused() {
        FaceProperties off = new FaceProperties(false, "http://x", "k", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, props.encKey());
        FaceAuthServiceImpl disabled = new FaceAuthServiceImpl(accounts, templates, meetings, personals, image -> null,
                new FaceTemplateCrypto(off), new FaceChallengeStore(off), new FaceRateLimiter(off), off, passwordEncoder, authService,
                java.time.Clock.systemUTC());

        assertThat(disabled.challenge(IP).enabled()).isFalse();
        assertThat(disabled.login(new FaceLoginRequest("alice", "x", List.of("a", "b")), IP).success()).isFalse();
        assertThat(disabled.status("alice").enabled()).isFalse();
    }
}
