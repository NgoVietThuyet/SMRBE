package vn.com.d2s.smr.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI tại /swagger-ui.html. Bấm "Authorize" và dán accessToken (không cần chữ "Bearer")
 * lấy từ POST /api/Auth/Login để gọi các API cần đăng nhập.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI smrOpenApi() {
        return new OpenAPI()
                .info(new Info().title("SMR API").version("v1").description("API của hệ thống Smart Meeting (SMR)"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
