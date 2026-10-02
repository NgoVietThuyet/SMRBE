package vn.com.d2s.smr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smr.auth")
public record AuthProperties(
        boolean publicRegistration,
        String registrationOrgId,
        String registrationTitleCode,
        boolean exposeResetToken
) {
    public AuthProperties {
        registrationOrgId = registrationOrgId == null ? "" : registrationOrgId.trim();
        registrationTitleCode = registrationTitleCode == null ? "" : registrationTitleCode.trim();
    }
}
