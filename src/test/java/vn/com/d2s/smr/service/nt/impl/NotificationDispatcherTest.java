package vn.com.d2s.smr.service.nt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.nt.Notification;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.repository.nt.NotificationRepository;
import vn.com.d2s.smr.service.nt.UserNotificationPublisher;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-08T01:00:00Z");

    @Mock private NotificationRepository notificationRepository;
    @Mock private MeetingInfoRepository meetingRepository;
    @Mock private MeetingPersonalRepository personalRepository;
    @Mock private MeetingTaskRepository taskRepository;
    @Mock private MeetingTaskShareRepository shareRepository;
    @Mock private AdAccountRepository accountRepository;
    @Mock private UserNotificationPublisher publisher;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private NotificationDispatcher dispatcher;
    private final List<Notification> saved = new ArrayList<>();
    private final Map<String, AdAccount> accounts = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(notificationRepository, meetingRepository, personalRepository,
                taskRepository, shareRepository, accountRepository, publisher, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
        for (String u : List.of("host", "b", "c", "d", "e")) {
            account(u, true);
        }
        when(accountRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<String> ids = inv.getArgument(0);
            List<AdAccount> out = new ArrayList<>();
            ids.forEach(id -> { if (accounts.containsKey(id)) out.add(accounts.get(id)); });
            return out;
        });
        when(accountRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(accounts.get(inv.<String>getArgument(0))));
        when(notificationRepository.findByRecipientUserNameAndDedupeKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            saved.add(n);
            return n;
        });
    }

    private void account(String userName, boolean active) {
        AdAccount a = new AdAccount();
        a.setUserName(userName);
        a.setFullName("Tên " + userName);
        a.setActive(active);
        accounts.put(userName, a);
    }

    private MeetingInfo meeting(String id, int status) {
        MeetingInfo m = new MeetingInfo();
        m.setId(id);
        m.setName("Họp kế hoạch");
        m.setStatus(status);
        m.setExpectedStartTime(LocalDateTime.of(2026, 10, 9, 7, 0));
        when(meetingRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(m));
        return m;
    }

    private void members(String meetingId, String... userNames) {
        List<MeetingPersonal> ps = new ArrayList<>();
        for (String u : userNames) {
            MeetingPersonal p = new MeetingPersonal();
            p.setMeetingId(meetingId);
            p.setUserName(u);
            p.setJoined(u.equals("c"));
            ps.add(p);
        }
        when(personalRepository.findByMeetingId(meetingId)).thenReturn(ps);
        for (String u : userNames) {
            when(personalRepository.existsByMeetingIdAndUserName(meetingId, u)).thenReturn(true);
        }
    }

    private MeetingTask task(String id, String createBy, String assignee, boolean isPublic, int level, String parentId) {
        MeetingTask t = new MeetingTask();
        t.setId(id);
        t.setTitle("Việc " + id);
        t.setCreateBy(createBy);
        t.setAssigneeUserName(assignee);
        t.setPublicTask(isPublic);
        t.setLevel(level);
        t.setParentId(parentId);
        when(taskRepository.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    private List<String> recipients() {
        return saved.stream().map(Notification::getRecipientUserName).toList();
    }

    @Test
    void invitedMembersReceiveButActorAndOutsidersDoNot() {
        meeting("m1", 1);
        members("m1", "host", "b", "c");

        // d không phải thành viên (client gửi bừa) → bị loại
        dispatcher.onMeetingInvited(new NotificationEvents.MeetingInvited("m1", "host", List.of("host", "b", "c", "d")));

        assertThat(recipients()).containsExactlyInAnyOrder("b", "c");
        Notification n = saved.get(0);
        assertThat(n.getType()).isEqualTo("MEETING_INVITED");
        assertThat(n.getTitle()).isEqualTo("Tên host mời bạn tham gia cuộc họp");
        assertThat(n.getResourceType()).isEqualTo("meeting");
        assertThat(n.getDedupeKey()).isEqualTo("MEETING_INVITED:m1");
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void draftMeetingDoesNotSendInvitations() {
        meeting("m1", 0);
        members("m1", "host", "b");

        dispatcher.onMeetingInvited(new NotificationEvents.MeetingInvited("m1", "host", List.of("b")));

        assertThat(saved).isEmpty();
    }

    @Test
    void inactiveAccountsAndGuestsAreSkipped() {
        meeting("m1", 1);
        members("m1", "host", "b", "guest_x");
        account("b", false);

        dispatcher.onMeetingRescheduled(new NotificationEvents.MeetingRescheduled("m1", "host"));

        assertThat(saved).isEmpty();
    }

    @Test
    void startedNotifiesOnlyMembersNotYetJoined() {
        meeting("m1", 2);
        members("m1", "host", "b", "c"); // c đã joined

        dispatcher.onMeetingStarted(new NotificationEvents.MeetingStarted("m1", "host"));

        assertThat(recipients()).containsExactly("b");
    }

    @Test
    void removedParticipantGetsNoLink() {
        dispatcher.onParticipantRemoved(new NotificationEvents.MeetingParticipantRemoved("m1", "Họp X", "host", "c"));

        assertThat(recipients()).containsExactly("c");
        assertThat(saved.get(0).getResourceType()).isEqualTo("none");
        assertThat(saved.get(0).getResourceId()).isNull();
    }

    @Test
    void repeatedEventUpdatesExistingRowInsteadOfDuplicating() {
        meeting("m1", 1);
        members("m1", "host", "b");
        Notification existing = new Notification();
        existing.setId("old");
        existing.setRecipientUserName("b");
        existing.setDedupeKey("MEETING_RESCHEDULED:m1");
        existing.setRead(true);
        when(notificationRepository.findByRecipientUserNameAndDedupeKey("b", "MEETING_RESCHEDULED:m1")).thenReturn(Optional.of(existing));

        dispatcher.onMeetingRescheduled(new NotificationEvents.MeetingRescheduled("m1", "host"));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getId()).isEqualTo("old");
        assertThat(saved.get(0).isRead()).isFalse(); // làm mới thành chưa đọc
    }

    @Test
    void privateTaskStatusOnlyReachesPeopleWhoCanView() {
        // Task riêng tư: người tạo b, phụ trách c → cả hai xem được; actor là c
        task("t1", "b", "c", false, 2, null);

        dispatcher.onTaskStatusChanged(new NotificationEvents.TaskStatusChanged("t1", "c", 0, 1));

        assertThat(recipients()).containsExactly("b");
        assertThat(saved.get(0).getTitle()).contains("“Đang thực hiện”");
    }

    @Test
    void taskAssignedToSelfIsSilent() {
        task("t1", "b", "b", false, 0, null);

        dispatcher.onTaskAssigned(new NotificationEvents.TaskAssigned("t1", "b", "b"));

        assertThat(saved).isEmpty();
    }

    @Test
    void completedChildNotifiesBoardOwnerWithWorkIdForLink() throws Exception {
        task("w1", "host", null, false, 0, null);
        task("l1", "b", null, false, 1, "w1");
        task("s1", "b", "c", false, 2, "l1");
        // host xem được việc nhỏ nhờ share
        when(shareRepository.existsByTaskIdAndUserName("s1", "host")).thenReturn(true);

        dispatcher.onTaskStatusChanged(new NotificationEvents.TaskStatusChanged("s1", "c", 1, 2));

        Map<String, Notification> byRecipient = saved.stream()
                .collect(Collectors.toMap(Notification::getRecipientUserName, Function.identity()));
        assertThat(byRecipient.keySet()).containsExactlyInAnyOrder("b", "host");
        assertThat(byRecipient.get("host").getType()).isEqualTo("TASK_COMPLETED");
        assertThat(objectMapper.readTree(byRecipient.get("host").getDataJson()).get("workId").asText()).isEqualTo("w1");
    }

    @Test
    void boardOwnerWhoCannotViewChildIsNotNotified() {
        task("w1", "host", null, false, 0, null);
        task("s1", "b", "c", false, 2, "w1");

        dispatcher.onTaskStatusChanged(new NotificationEvents.TaskStatusChanged("s1", "c", 1, 2));

        assertThat(recipients()).containsExactly("b");
    }

    @SuppressWarnings("unchecked")
    @Test
    void pushesRealtimeOnlyToRecipients() {
        meeting("m1", 1);
        members("m1", "host", "b");
        when(notificationRepository.countByRecipientUserNameAndReadFalse("b")).thenReturn(3L);

        dispatcher.onMeetingInvited(new NotificationEvents.MeetingInvited("m1", "host", List.of("b")));

        ArgumentCaptor<Collection<String>> users = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishToUsers(users.capture(), eq("NotificationCreated"), payload.capture());
        assertThat(users.getValue()).containsExactly("b");
        assertThat(((Map<String, Object>) payload.getValue()).get("unreadCount")).isEqualTo(3L);
        verify(publisher, never()).publishToUsers(eq(List.of("host")), anyString(), any());
    }
}
