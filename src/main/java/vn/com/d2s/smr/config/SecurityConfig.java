package vn.com.d2s.smr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.com.d2s.smr.dto.common.ApiResponse;
import vn.com.d2s.smr.service.ad.impl.JwtTokenService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ObjectMapper objectMapper,
            AdminAccess adminAccess,
            MenuAccess menuAccess,
            GuestScope guestScope
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/Auth/Login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/Auth/Refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/Auth/Register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/Auth/ForgotPassword").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/Auth/ResetPassword").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/Auth/GuestSession").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/File/UploadFilesRecord").permitAll()
                        .requestMatchers("/meetinghub/**").permitAll()
                        // P10: màn /join/:roomCode cho khách chưa đăng nhập
                        .requestMatchers(HttpMethod.GET, "/api/Meeting/public/*").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Swagger UI + tài liệu OpenAPI (OpenApiConfig)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Quản trị: SUPER_ADMIN, hoặc tài khoản được gán quyền vào đúng menu (ADMIN_*) tương ứng.
                        // Các GET danh mục (summary, organization-tree, titles, employees) vẫn mở cho người đã đăng nhập.
                        .requestMatchers(HttpMethod.GET, "/api/human-resources/*/*/permissions").access(menuAccess.requires("ADMIN_PERMISSIONS"))
                        .requestMatchers(HttpMethod.PUT, "/api/human-resources/*/*/permissions").access(menuAccess.requires("ADMIN_PERMISSIONS"))
                        .requestMatchers(HttpMethod.GET, "/api/human-resources/employees/*/effective-permissions").access(menuAccess.requires("ADMIN_PERMISSIONS"))
                        .requestMatchers(HttpMethod.POST, "/api/human-resources/organizations/**").access(menuAccess.requires("ADMIN_ORGANIZATIONS"))
                        .requestMatchers(HttpMethod.PUT, "/api/human-resources/organizations/**").access(menuAccess.requires("ADMIN_ORGANIZATIONS"))
                        .requestMatchers(HttpMethod.DELETE, "/api/human-resources/organizations/**").access(menuAccess.requires("ADMIN_ORGANIZATIONS"))
                        .requestMatchers(HttpMethod.POST, "/api/human-resources/titles/**").access(menuAccess.requires("ADMIN_TITLES"))
                        .requestMatchers(HttpMethod.PUT, "/api/human-resources/titles/**").access(menuAccess.requires("ADMIN_TITLES"))
                        .requestMatchers(HttpMethod.DELETE, "/api/human-resources/titles/**").access(menuAccess.requires("ADMIN_TITLES"))
                        .requestMatchers(HttpMethod.POST, "/api/human-resources/employees/**", "/api/human-resources/directory-employees").access(menuAccess.requires("ADMIN_ACCOUNTS"))
                        .requestMatchers(HttpMethod.PUT, "/api/human-resources/employees/**").access(menuAccess.requires("ADMIN_ACCOUNTS"))
                        .requestMatchers(HttpMethod.DELETE, "/api/human-resources/employees/**").access(menuAccess.requires("ADMIN_ACCOUNTS"))
                        .requestMatchers("/api/admin/reports/**").access(menuAccess.requires("ADMIN_STATISTICS"))
                        .requestMatchers("/api/admin/audit-logs/**").access(menuAccess.requires("ADMIN_AUDIT_LOGS"))
                        .requestMatchers("/api/admin/settings/**", "/api/admin/minutes-templates/**").access(menuAccess.requires("ADMIN_SETTINGS"))
                        .requestMatchers("/api/admin/**").access(adminAccess)
                        .requestMatchers(HttpMethod.POST, "/api/human-resources/**").access(adminAccess)
                        .requestMatchers(HttpMethod.PUT, "/api/human-resources/**").access(adminAccess)
                        .requestMatchers(HttpMethod.DELETE, "/api/human-resources/**").access(adminAccess)
                        // Token khách chỉ dùng được cho đúng cuộc họp của nó.
                        .anyRequest().access(guestScope))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure("Vui lòng đăng nhập để thực hiện tác vụ."));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure("Bạn không có quyền thực hiện tác vụ này."));
                        }));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, AccountTokenValidator accountTokenValidator) {
        SecretKey key = new SecretKeySpec(properties.key().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "JWT audience không hợp lệ.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, accountTokenValidator));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(JwtTokenService.NAME_CLAIM);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        // Dạng mẫu: cho phép ghi "http://sso.d2s.com.vn:*" (mọi cổng) hoặc "https://*.d2s.com.vn".
        configuration.setAllowedOriginPatterns(properties.allowedOrigins().stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // FE khác domain/cổng với BE: cho trình duyệt đọc tên file khi tải DOCX/XLSX.
        configuration.setExposedHeaders(List.of("Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
