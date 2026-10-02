package vn.com.d2s.smr.service.ad.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.com.d2s.smr.config.JwtProperties;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.service.ad.TokenPair;
import vn.com.d2s.smr.service.ad.TokenService;
import vn.com.d2s.smr.service.ad.ResetTokenIdentity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService implements TokenService {

    public static final String NAME_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name";
    public static final String EMAIL_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";
    public static final String PURPOSE_CLAIM = "Purpose";
    public static final String REFRESH_PURPOSE = "refresh";
    public static final String RESET_PURPOSE = "reset";

    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public TokenPair issueFor(AdAccount account) {
        Instant now = clock.instant();

        JWTClaimsSet accessClaims = baseClaims(now)
                .expirationTime(Date.from(now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES)))
                .claim(NAME_CLAIM, account.getUserName())
                .claim(EMAIL_CLAIM, account.getEmail())
                .claim("FullName", account.getFullName())
                .claim("TokenVersion", Integer.toString(account.getTokenVersion()))
                .build();

        JWTClaimsSet refreshClaims = baseClaims(now)
                .expirationTime(Date.from(now.plus(properties.refreshTokenExpirationDays(), ChronoUnit.DAYS)))
                .claim(NAME_CLAIM, account.getUserName())
                .claim(PURPOSE_CLAIM, REFRESH_PURPOSE)
                .build();

        return new TokenPair(sign(accessClaims), sign(refreshClaims));
    }

    @Override
    public Optional<String> resolveRefreshTokenOwner(String refreshToken) {
        return resolvePurposeTokenOwner(refreshToken, REFRESH_PURPOSE);
    }

    @Override
    public String issueResetToken(AdAccount account) {
        Instant now = clock.instant();
        JWTClaimsSet resetClaims = baseClaims(now)
                .expirationTime(Date.from(now.plus(properties.resetTokenExpirationMinutes(), ChronoUnit.MINUTES)))
                .claim(NAME_CLAIM, account.getUserName())
                .claim("TokenVersion", account.getTokenVersion())
                .claim(PURPOSE_CLAIM, RESET_PURPOSE)
                .build();
        return sign(resetClaims);
    }

    @Override
    public String issueForGuest(String guestUserName, String displayName, String meetingId) {
        Instant now = clock.instant();
        JWTClaimsSet guestClaims = baseClaims(now)
                .expirationTime(Date.from(now.plus(4, ChronoUnit.HOURS)))
                .claim(NAME_CLAIM, guestUserName)
                .claim("FullName", displayName)
                .claim("IsGuest", "true")
                .claim("MeetingId", meetingId)
                .claim("TokenVersion", "1")
                .build();
        return sign(guestClaims);
    }

    @Override
    public Optional<ResetTokenIdentity> resolveResetToken(String resetToken) {
        Optional<JWTClaimsSet> claims = resolvePurposeToken(resetToken, RESET_PURPOSE);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        try {
            String userName = claims.get().getStringClaim(NAME_CLAIM);
            Object versionClaim = claims.get().getClaim("TokenVersion");
            Number version = versionClaim instanceof Number number ? number : null;
            if (userName == null || userName.isBlank() || version == null) {
                return Optional.empty();
            }
            return Optional.of(new ResetTokenIdentity(userName, version.intValue()));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Optional<String> resolvePurposeTokenOwner(String token, String expectedPurpose) {
        Optional<JWTClaimsSet> claims = resolvePurposeToken(token, expectedPurpose);
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        try {
            String userName = claims.get().getStringClaim(NAME_CLAIM);
            return userName == null || userName.isBlank() ? Optional.empty() : Optional.of(userName);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private Optional<JWTClaimsSet> resolvePurposeToken(String token, String expectedPurpose) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            if (!jwt.verify(new MACVerifier(properties.key().getBytes(StandardCharsets.UTF_8)))) {
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            if (!properties.issuer().equals(claims.getIssuer())
                    || !claims.getAudience().contains(properties.audience())
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(now)
                    || !expectedPurpose.equals(claims.getStringClaim(PURPOSE_CLAIM))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private JWTClaimsSet.Builder baseClaims(Instant now) {
        return new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .audience(properties.audience())
                .issueTime(Date.from(now))
                .jwtID(UUID.randomUUID().toString());
    }

    private String sign(JWTClaimsSet claims) {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(properties.key().getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Không thể ký JWT.", exception);
        }
    }
}
