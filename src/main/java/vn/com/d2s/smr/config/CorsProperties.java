package vn.com.d2s.smr.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "smr.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}

