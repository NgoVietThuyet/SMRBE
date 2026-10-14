package vn.com.d2s.smr.service.nt.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.com.d2s.smr.dto.nt.NotificationPageResponse;
import vn.com.d2s.smr.entity.nt.Notification;
import vn.com.d2s.smr.repository.nt.NotificationRepository;
import vn.com.d2s.smr.service.nt.UserNotificationPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-08T01:00:00Z");

    @Mock private NotificationRepository repository;
    @Mock private UserNotificationPublisher publisher;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(repository, publisher, JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Notification notification(String id, String recipient, LocalDateTime createDate) {
        Notification n = new Notification();
        n.setId(id);
        n.setRecipientUserName(recipient);
        n.setType("TASK_ASSIGNED");
        n.setTitle("Tiêu đề");
        n.setResourceType("task");
        n.setDedupeKey("k" + id);
        n.setCreateDate(createDate);
        return n;
    }

    @Test
    void cannotMarkReadSomeoneElsesNotification() {
        when(repository.findByIdAndRecipientUserName("n1", "b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("b", "n1", true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
        verify(publisher, never()).publishToUsers(any(), anyString(), any());
    }

    @Test
    void cannotDeleteSomeoneElsesNotification() {
        when(repository.findByIdAndRecipientUserName("n1", "b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("b", "n1")).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).delete(any(Notification.class));
    }

    @Test
    void markReadUpdatesAndSyncsOwnTabsOnly() {
        Notification n = notification("n1", "b", LocalDateTime.of(2026, 10, 8, 0, 0));
        when(repository.findByIdAndRecipientUserName("n1", "b")).thenReturn(Optional.of(n));
        when(repository.countByRecipientUserNameAndReadFalse("b")).thenReturn(2L);

        long unread = service.markRead("b", "n1", true);

        assertThat(unread).isEqualTo(2L);
        assertThat(n.isRead()).isTrue();
        assertThat(n.getReadAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(publisher).publishToUsers(eq(List.of("b")), eq("NotificationsRead"), any());
    }

    @Test
    void listReturnsCursorWhenMorePagesExist() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 8, 0, 0);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                notification("n3", "b", t.plusMinutes(2)),
                notification("n2", "b", t.plusMinutes(1)),
                notification("n1", "b", t)
        )));
        when(repository.countByRecipientUserNameAndReadFalse("b")).thenReturn(3L);

        NotificationPageResponse page = service.list("b", false, null, 2);

        assertThat(page.items()).extracting("id").containsExactly("n3", "n2");
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.unreadCount()).isEqualTo(3L);
        assertThat(page.items().get(0).createDate()).isEqualTo(t.plusMinutes(2).toInstant(ZoneOffset.UTC));

        // cursor giải mã được ở lần gọi sau
        service.list("b", false, page.nextCursor(), 2);
    }

    @Test
    void invalidCursorIsRejected() {
        assertThatThrownBy(() -> service.list("b", false, "%%%", 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readAllUsesBeforeCutoff() {
        Instant before = Instant.parse("2026-10-08T00:30:00Z");

        service.markAllRead("b", before);

        verify(repository).markAllRead(eq("b"), eq(LocalDateTime.ofInstant(before, ZoneOffset.UTC)),
                eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
    }
}
