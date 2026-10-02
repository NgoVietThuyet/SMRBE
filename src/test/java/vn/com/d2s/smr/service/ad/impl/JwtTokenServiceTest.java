package vn.com.d2s.smr.service.ad.impl;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.config.JwtProperties;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.service.ad.TokenPair;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-30T01:00:00Z");

    @Test
    void issuedTokensMatchDotNetClaimsAndExpiryContract() throws Exception {
        JwtProperties properties = new JwtProperties(KEY, "SMR.Api", "SMR.Web", 1440, 7, 15);
        JwtTokenService service = new JwtTokenService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        AdAccount account = account();

        TokenPair pair = service.issueFor(account);
        SignedJWT accessToken = SignedJWT.parse(pair.accessToken());
        SignedJWT refreshToken = SignedJWT.parse(pair.refreshToken());

        assertThat(accessToken.verify(new MACVerifier(KEY.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(accessToken.getJWTClaimsSet().getIssuer()).isEqualTo("SMR.Api");
        assertThat(accessToken.getJWTClaimsSet().getAudience()).containsExactly("SMR.Web");
        assertThat(accessToken.getJWTClaimsSet().getStringClaim(JwtTokenService.NAME_CLAIM)).isEqualTo("admin");
        assertThat(accessToken.getJWTClaimsSet().getStringClaim(JwtTokenService.EMAIL_CLAIM)).isEqualTo("admin@example.com");
        assertThat(accessToken.getJWTClaimsSet().getStringClaim("FullName")).isEqualTo("Quản trị viên");
        assertThat(accessToken.getJWTClaimsSet().getStringClaim("TokenVersion")).isEqualTo("3");
        assertThat(accessToken.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(1440 * 60L));

        assertThat(refreshToken.verify(new MACVerifier(KEY.getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(refreshToken.getJWTClaimsSet().getStringClaim("Purpose")).isEqualTo("refresh");
        assertThat(refreshToken.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(7 * 86400L));
    }

    @Test
    void resolvesOwnerOnlyFromValidRefreshToken() {
        JwtTokenService service = serviceAt(NOW);
        TokenPair pair = service.issueFor(account());

        assertThat(service.resolveRefreshTokenOwner(pair.refreshToken())).contains("admin");
        assertThat(service.resolveRefreshTokenOwner(pair.accessToken())).isEmpty();
        assertThat(service.resolveRefreshTokenOwner("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsExpiredRefreshToken() {
        JwtTokenService issuer = serviceAt(NOW);
        String refreshToken = issuer.issueFor(account()).refreshToken();
        JwtTokenService validator = serviceAt(NOW.plusSeconds(7 * 86400L));

        assertThat(validator.resolveRefreshTokenOwner(refreshToken)).isEmpty();
    }

    @Test
    void rejectsRefreshTokensWithWrongSignatureIssuerOrAudience() {
        JwtTokenService validator = serviceAt(NOW);
        JwtTokenService wrongSignatureIssuer = new JwtTokenService(
                new JwtProperties("abcdef0123456789abcdef0123456789", "SMR.Api", "SMR.Web", 1440, 7, 15),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        JwtTokenService wrongIssuer = new JwtTokenService(
                new JwtProperties(KEY, "Other.Api", "SMR.Web", 1440, 7, 15),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        JwtTokenService wrongAudience = new JwtTokenService(
                new JwtProperties(KEY, "SMR.Api", "Other.Web", 1440, 7, 15),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(validator.resolveRefreshTokenOwner(wrongSignatureIssuer.issueFor(account()).refreshToken())).isEmpty();
        assertThat(validator.resolveRefreshTokenOwner(wrongIssuer.issueFor(account()).refreshToken())).isEmpty();
        assertThat(validator.resolveRefreshTokenOwner(wrongAudience.issueFor(account()).refreshToken())).isEmpty();
    }

    @Test
    void resetTokenContainsAccountVersionAndRejectsWrongPurpose() {
        JwtTokenService service = serviceAt(NOW);
        AdAccount account = account();

        String resetToken = service.issueResetToken(account);

        assertThat(service.resolveResetToken(resetToken)).hasValueSatisfying(identity -> {
            assertThat(identity.userName()).isEqualTo("admin");
            assertThat(identity.tokenVersion()).isEqualTo(3);
        });
        assertThat(service.resolveRefreshTokenOwner(resetToken)).isEmpty();
        assertThat(service.resolveResetToken(service.issueFor(account).refreshToken())).isEmpty();
    }

    private static JwtTokenService serviceAt(Instant instant) {
        JwtProperties properties = new JwtProperties(KEY, "SMR.Api", "SMR.Web", 1440, 7, 15);
        return new JwtTokenService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static AdAccount account() {
        AdAccount account = new AdAccount();
        account.setUserName("admin");
        account.setEmail("admin@example.com");
        account.setFullName("Quản trị viên");
        account.setTokenVersion(3);
        return account;
    }
}
