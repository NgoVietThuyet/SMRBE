package vn.com.d2s.smr.service.mt.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskStatusRequest;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.service.mt.TaskBoardService;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P12: quy tắc quyền theo bảng trong TaskServiceImpl (khi có TaskAccess). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceBoardRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");

    @Mock private MeetingTaskRepository taskRepository;
    @Mock private MeetingTaskShareRepository shareRepository;
    @Mock private MeetingInfoRepository meetingRepository;
    @Mock private MeetingPersonalRepository personalRepository;
    @Mock private AdAccountRepository accountRepository;
    @Mock private TaskBoardService board;

    private final Map<String, MeetingTask> db = new HashMap<>();
    private final List<Object> events = new ArrayList<>();
    private TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TaskServiceImpl(taskRepository, shareRepository, meetingRepository, personalRepository, accountRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service.setAccess(new TaskAccess(taskRepository, shareRepository, personalRepository));
        service.setBoard(board);
        service.setEventPublisher(events::add);

        put(task("W", null, 0, "owner", null));
        put(task("L1", "W", 1, "owner", null));
        put(task("c1", "L1", 2, "owner", "worker"));
        put(task("c2", "L1", 2, "owner", null));

        when(taskRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(db.get((String) i.getArgument(0))));
        when(taskRepository.findByParentId(anyString())).thenAnswer(i -> children((String) i.getArgument(0)));
        when(taskRepository.findByParentIdIn(anyCollection())).thenAnswer(i -> {
            List<MeetingTask> out = new ArrayList<>();
            ((Collection<String>) i.getArgument(0)).forEach(p -> out.addAll(children(p)));
            return out;
        });
        doAnswer(i -> db.remove(((MeetingTask) i.getArgument(0)).getId())).when(taskRepository).delete(any(MeetingTask.class));
        when(shareRepository.findByTaskIdAndUserName(anyString(), anyString())).thenReturn(Optional.empty());
        when(shareRepository.findByTaskId(anyString())).thenReturn(List.of());
    }

    @Test
    void assigneeCanCompleteOwnCardAndCreatorIsNotified() {
        service.updateTaskStatus("worker", "c1", new UpdateTaskStatusRequest(2));

        assertThat(db.get("c1").getStatus()).isEqualTo(2);
        assertThat(events).anyMatch(e -> e instanceof NotificationEvents.TaskStatusChanged);
    }

    @Test
    void assigneeCannotCompleteSomeoneElsesCard() {
        assertThatThrownBy(() -> service.updateTaskStatus("worker", "c2", new UpdateTaskStatusRequest(2)))
                .isInstanceOf(SecurityException.class); // xem được bảng nhưng không phải việc của mình
    }

    @Test
    void viewShareGets403OnUpdate() {
        MeetingTaskShare s = new MeetingTaskShare();
        s.setTaskId("W");
        s.setUserName("viewer");
        s.setPermission(0);
        when(shareRepository.findByTaskIdAndUserName("W", "viewer")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.updateTask("viewer", "c2",
                new UpdateTaskRequest("x", null, null, null, 0, 1, null, null, null)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.updateTaskStatus("viewer", "c2", new UpdateTaskStatusRequest(2)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void ownerDeletesListWithAllCards() {
        service.deleteTask("owner", "L1");

        assertThat(db).containsOnlyKeys("W");
        verify(board).purge(eq(List.of("L1", "c1", "c2")), eq(null));
    }

    @Test
    void onlyOwnerDeletesBoard() {
        MeetingTaskShare s = new MeetingTaskShare();
        s.setTaskId("W");
        s.setUserName("editor");
        s.setPermission(1);
        when(shareRepository.findByTaskIdAndUserName("W", "editor")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.deleteTask("editor", "W")).isInstanceOf(SecurityException.class);
        service.deleteTask("editor", "c2"); // cột / thẻ: người có quyền Sửa được xoá
        assertThat(db).doesNotContainKey("c2");
    }

    private void put(MeetingTask t) {
        db.put(t.getId(), t);
    }

    private List<MeetingTask> children(String parentId) {
        return db.values().stream().filter(t -> parentId.equals(t.getParentId()))
                .sorted((a, b) -> a.getId().compareTo(b.getId())).toList();
    }

    private static MeetingTask task(String id, String parent, int level, String createBy, String assignee) {
        MeetingTask t = new MeetingTask();
        t.setId(id);
        t.setParentId(parent);
        t.setLevel(level);
        t.setTitle(id);
        t.setCreateBy(createBy);
        t.setCreateDate(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        t.setAssigneeUserName(assignee);
        return t;
    }
}
