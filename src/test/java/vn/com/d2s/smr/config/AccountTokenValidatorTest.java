package vn.com.d2s.smr.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.service.ad.impl.JwtTokenService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountTokenValidatorTest {

    @Mock private AdAccountRepository accountRepository;

    @Test
    void acceptsActiveAccountWithMatchingTokenVersion() {
        AdAccount account = account(2, true);
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        AccountTokenValidator validator = new AccountTokenValidator(accountRepository);

        OAuth2TokenValidatorResult result = validator.validate(jwt("admin", "2"));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsRevokedVersionAndLockedAccount() {
        AdAccount account = account(3, true);
        when(accountRepository.findById("admin")).thenReturn(Optional.of(account));
        AccountTokenValidator validator = new AccountTokenValidator(accountRepository);

        assertThat(validator.validate(jwt("admin", "2")).hasErrors()).isTrue();

        account.setActive(false);
        assertThat(validator.validate(jwt("admin", "3")).hasErrors()).isTrue();
    }

    private static Jwt jwt(String userName, String tokenVersion) {
        Instant now = Instant.parse("2026-09-30T01:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim(JwtTokenService.NAME_CLAIM, userName)
                .claim("TokenVersion", tokenVersion)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }

    private static AdAccount account(int version, boolean active) {
        AdAccount account = new AdAccount();
        account.setUserName("admin");
        account.setTokenVersion(version);
        account.setActive(active);
        return account;
    }
}
