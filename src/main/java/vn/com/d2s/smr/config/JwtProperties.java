package vn.com.d2s.smr.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smr.jwt")
public record JwtProperties(
        @NotBlank String key,
        @NotBlank String issuer,
        @NotBlank String audience,
        @Min(1) long accessTokenExpirationMinutes,
        @Min(1) long refreshTokenExpirationDays,
        @Min(1) long resetTokenExpirationMinutes
) {
    public JwtProperties {
        if (key != null && !key.isBlank() && key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_KEY phải có ít nhất 32 byte cho HS256.");
        }
    }
}
