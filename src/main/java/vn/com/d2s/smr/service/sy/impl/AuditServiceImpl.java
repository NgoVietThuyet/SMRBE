package vn.com.d2s.smr.service.sy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.sy.AuditEntry;
import vn.com.d2s.smr.dto.sy.AuditLogResponse;
import vn.com.d2s.smr.entity.sy.SyAuditLog;
import vn.com.d2s.smr.repository.sy.SyAuditLogRepository;
import vn.com.d2s.smr.service.sy.AuditService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditServiceImpl.class);
    private static final List<String> SENSITIVE_KEY_PARTS = List.of("password", "token", "secret", "otp");
    private static final int MAX_PAYLOAD_LENGTH = 8000;

    private final SyAuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditServiceImpl(SyAuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEntry entry) {
        try {
            SyAuditLog log = new SyAuditLog();
            log.setId(UUID.randomUUID().toString());
            log.setCategory(truncate(entry.category(), 30));
            log.setAction(truncate(entry.action(), 100));
            log.setActorUserName(truncate(entry.actorUserName(), 450));
            log.setTargetType(truncate(entry.targetType(), 50));
            log.setTargetId(truncate(entry.targetId(), 450));
            log.setSuccess(entry.success());
            log.setHttpStatus(entry.httpStatus());
            log.setIp(truncate(entry.ip(), 64));
            log.setUserAgent(truncate(entry.userAgent(), 500));
            log.setPayloadJson(toJson(sanitize(entry.payload())));
            log.setOccurredAt(LocalDateTime.now(clock));
            repository.save(log);
        } catch (RuntimeException ex) {
            LOG.warn("Không ghi được nhật ký {}/{}: {}", entry.category(), entry.action(), ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResultResponse<AuditLogResponse> search(
            LocalDateTime from, LocalDateTime to, String category, String actor, Boolean success,
            String keyword, int page, int pageSize
    ) {
        int validatedPage = Math.max(1, page);
        int validatedPageSize = Math.min(100, Math.max(1, pageSize));
        Page<SyAuditLog> result = repository.search(
                from, to, blankToNull(category), blankToNull(actor), success, blankToNull(keyword),
                PageRequest.of(validatedPage - 1, validatedPageSize)
        );
        List<AuditLogResponse> items = result.getContent().stream().map(AuditServiceImpl::toResponse).toList();
        return PagedResultResponse.of(items, validatedPage, validatedPageSize, result.getTotalElements());
    }

    /** Bỏ mọi khóa có chứa password/token/secret/otp ở mọi cấp lồng nhau. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitize(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (SENSITIVE_KEY_PARTS.stream().anyMatch(lower::contains)) {
                return;
            }
            if (value instanceof Map<?, ?> nested) {
                clean.put(key, sanitize((Map<String, Object>) nested));
            } else if (value instanceof Collection<?> list) {
                clean.put(key, list.stream()
                        .map(item -> item instanceof Map<?, ?> m ? sanitize((Map<String, Object>) m) : item)
                        .toList());
            } else {
                clean.put(key, value);
            }
        });
        return clean;
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(payload), MAX_PAYLOAD_LENGTH);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static AuditLogResponse toResponse(SyAuditLog l) {
        return new AuditLogResponse(
                l.getId(), l.getCategory(), l.getAction(), l.getActorUserName(), l.getTargetType(), l.getTargetId(),
                l.isSuccess(), l.getHttpStatus(), l.getIp(), l.getUserAgent(), l.getPayloadJson(), l.getOccurredAt()
        );
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
