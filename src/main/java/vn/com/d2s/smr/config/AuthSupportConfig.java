package vn.com.d2s.smr.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.com.d2s.smr.service.ad.PasswordResetNotifier;

import java.time.Clock;

@Configuration
public class AuthSupportConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(PasswordResetNotifier.class)
    PasswordResetNotifier passwordResetNotifier() {
        return (account, resetToken) -> {
            // Adapter email/SMS sẽ thay thế bean này ở môi trường triển khai.
            // Không ghi reset token vào log để tránh lộ thông tin xác thực.
        };
    }
}
