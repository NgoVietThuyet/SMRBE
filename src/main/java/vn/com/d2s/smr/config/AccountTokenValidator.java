package vn.com.d2s.smr.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.service.ad.impl.JwtTokenService;

@Component
public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED_TOKEN = new OAuth2Error(
            "invalid_token",
            "JWT đã bị thu hồi hoặc tài khoản không còn hoạt động.",
            null
    );

    private final AdAccountRepository accountRepository;

    public AccountTokenValidator(AdAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if ("true".equalsIgnoreCase(token.getClaimAsString("IsGuest"))) {
            return OAuth2TokenValidatorResult.success();
        }

        String userName = token.getClaimAsString(JwtTokenService.NAME_CLAIM);
        String tokenVersion = token.getClaimAsString("TokenVersion");
        if (userName == null || tokenVersion == null) {
            return OAuth2TokenValidatorResult.failure(REVOKED_TOKEN);
        }

        AdAccount account = accountRepository.findById(userName).orElse(null);
        if (account == null
                || !account.isActive()
                || !Integer.toString(account.getTokenVersion()).equals(tokenVersion)) {
            return OAuth2TokenValidatorResult.failure(REVOKED_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
