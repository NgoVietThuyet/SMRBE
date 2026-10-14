package vn.com.d2s.smr.service.sy.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.d2s.smr.dto.sy.AuditEntry;
import vn.com.d2s.smr.entity.sy.SyAuditLog;
import vn.com.d2s.smr.repository.sy.SyAuditLogRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private SyAuditLogRepository repository;

    private AuditServiceImpl service() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T03:00:00Z"), ZoneOffset.UTC);
        return new AuditServiceImpl(repository, new ObjectMapper(), clock);
    }

    @Test
    void sanitizeRemovesSensitiveKeysAtEveryLevel() {
        Map<String, Object> payload = Map.of(
                "request", Map.of("userName", "admin", "password", "secret-value", "newPassword", "x"),
                "refreshToken", "abc",
                "items", List.of(Map.of("resetToken", "t", "name", "ok"))
        );

        Map<String, Object> clean = AuditServiceImpl.sanitize(payload);

        assertThat(clean).doesNotContainKey("refreshToken");
        assertThat((Map<String, Object>) clean.get("request")).containsOnlyKeys("userName");
        assertThat(clean.get("items").toString()).contains("name").doesNotContain("resetToken");
    }

    @Test
    void logStoresEntryWithoutPassword() {
        service().log(new AuditEntry("AUTH", "LOGIN", "admin", "ACCOUNT", "admin", false, 400,
                "127.0.0.1", "JUnit", Map.of("request", Map.of("userName", "admin", "password", "wrong"))));

        ArgumentCaptor<SyAuditLog> captor = ArgumentCaptor.forClass(SyAuditLog.class);
        verify(repository).save(captor.capture());
        SyAuditLog saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("AUTH");
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getHttpStatus()).isEqualTo(400);
        assertThat(saved.getPayloadJson()).contains("admin").doesNotContain("wrong").doesNotContain("password");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void logNeverThrowsWhenRepositoryFails() {
        when(repository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service().log(new AuditEntry("AUTH", "LOGOUT", "admin", null, null, true, 200,
                null, null, null))).doesNotThrowAnyException();
    }
}
