package vn.com.d2s.smr.service.nt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.com.d2s.smr.dto.nt.NotificationItemResponse;
import vn.com.d2s.smr.dto.nt.NotificationPageResponse;
import vn.com.d2s.smr.entity.nt.Notification;
import vn.com.d2s.smr.repository.nt.NotificationRepository;
import vn.com.d2s.smr.service.nt.NotificationService;
import vn.com.d2s.smr.service.nt.UserNotificationPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository repository;
    private final UserNotificationPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public NotificationServiceImpl(NotificationRepository repository, UserNotificationPublisher publisher, ObjectMapper objectMapper) {
        this(repository, publisher, objectMapper, Clock.systemUTC());
    }

    NotificationServiceImpl(NotificationRepository repository, UserNotificationPublisher publisher, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse list(String userName, boolean unreadOnly, String cursor, int pageSize) {
        int size = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        Cursor after = Cursor.decode(cursor);

        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("recipientUserName"), userName));
            if (unreadOnly) {
                ps.add(cb.isFalse(root.get("read")));
            }
            if (after != null) {
                // (createDate, id) < (cursor.createDate, cursor.id) — thứ tự ổn định khi trùng thời điểm
                ps.add(cb.or(
                        cb.lessThan(root.get("createDate"), after.createDate()),
                        cb.and(cb.equal(root.get("createDate"), after.createDate()), cb.lessThan(root.get("id"), after.id()))
                ));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };

        List<Notification> rows = repository.findAll(
                spec,
                PageRequest.of(0, size + 1, Sort.by(Sort.Order.desc("createDate"), Sort.Order.desc("id")))
        ).getContent();

        boolean hasMore = rows.size() > size;
        List<Notification> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? Cursor.encode(page.get(page.size() - 1)) : null;
        List<NotificationItemResponse> items = page.stream().map(n -> NotificationMapper.toResponse(n, objectMapper)).toList();

        return new NotificationPageResponse(items, nextCursor, repository.countByRecipientUserNameAndReadFalse(userName));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String userName) {
        return repository.countByRecipientUserNameAndReadFalse(userName);
    }

    @Override
    @Transactional
    public long markRead(String userName, String id, boolean isRead) {
        Notification n = repository.findByIdAndRecipientUserName(id, userName)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo."));
        if (n.isRead() != isRead) {
            n.setRead(isRead);
            n.setReadAt(isRead ? LocalDateTime.now(clock) : null);
            repository.save(n);
        }
        long unread = repository.countByRecipientUserNameAndReadFalse(userName);
        syncOtherTabs(userName, List.of(id), isRead, false, unread);
        return unread;
    }

    @Override
    @Transactional
    public long markAllRead(String userName, Instant before) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = before != null ? LocalDateTime.ofInstant(before, ZoneOffset.UTC) : now;
        repository.markAllRead(userName, cutoff, now);
        long unread = repository.countByRecipientUserNameAndReadFalse(userName);
        syncOtherTabs(userName, null, true, false, unread);
        return unread;
    }

    @Override
    @Transactional
    public void delete(String userName, String id) {
        Notification n = repository.findByIdAndRecipientUserName(id, userName)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo."));
        repository.delete(n);
        syncOtherTabs(userName, List.of(id), true, true, repository.countByRecipientUserNameAndReadFalse(userName));
    }

    /** Đồng bộ số chưa đọc giữa các tab/thiết bị của cùng user, sau khi commit. */
    private void syncOtherTabs(String userName, List<String> ids, boolean isRead, boolean deleted, long unread) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("ids", ids); // null = tất cả
        payload.put("isRead", isRead);
        payload.put("deleted", deleted);
        payload.put("unreadCount", unread);
        Runnable push = () -> publisher.publishToUsers(List.of(userName), "NotificationsRead", payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    /** Cursor mờ: base64url("createDateIso|id"). */
    record Cursor(LocalDateTime createDate, String id) {

        static String encode(Notification n) {
            String raw = n.getCreateDate() + "|" + n.getId();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int sep = raw.indexOf('|');
                return new Cursor(LocalDateTime.parse(raw.substring(0, sep)), raw.substring(sep + 1));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Cursor không hợp lệ.");
            }
        }
    }
}
