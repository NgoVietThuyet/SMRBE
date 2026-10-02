package vn.com.d2s.smr.service.mt.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.com.d2s.smr.dto.mt.task.CreateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.TaskDetailResponse;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;
import vn.com.d2s.smr.dto.mt.task.TaskSearchRequest;
import vn.com.d2s.smr.dto.mt.task.TaskSearchResultResponse;
import vn.com.d2s.smr.dto.mt.task.TaskShareInputRequest;
import vn.com.d2s.smr.dto.mt.task.TaskVisibilityRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskStatusRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-30T01:00:00Z");

    @Mock
    private MeetingTaskRepository taskRepository;

    @Mock
    private MeetingTaskShareRepository shareRepository;

    @Mock
    private MeetingInfoRepository meetingRepository;

    @Mock
    private MeetingPersonalRepository personalRepository;

    @Mock
    private AdAccountRepository accountRepository;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(
                taskRepository,
                shareRepository,
                meetingRepository,
                personalRepository,
                accountRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void searchTasksReturnsPagedResultsAndSummary() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        when(taskRepository.count(any(Specification.class))).thenReturn(1L);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        TaskSearchResultResponse result = taskService.searchTasks(
                "user1",
                new TaskSearchRequest("all", "", null, null, null, null, null, null, 1, 10)
        );

        assertThat(result.totalItems()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("t1");
        assertThat(result.summary().total()).isEqualTo(1);
    }

    @Test
    void getTaskReturnsDetailForViewableTask() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        when(taskRepository.findOne(any(Specification.class))).thenReturn(Optional.of(task));

        TaskDetailResponse detail = taskService.getTask("user1", "t1");

        assertThat(detail.id()).isEqualTo("t1");
        assertThat(detail.isCreator()).isTrue();
    }

    @Test
    void createTaskSavesTask() {
        CreateTaskRequest req = new CreateTaskRequest(
                null, null, 0, "Nhiệm vụ 1", "Mô tả", null, null, 0, 1, false, null
        );

        TaskListItemResponse response = taskService.createTask("user1", req);

        assertThat(response.title()).isEqualTo("Nhiệm vụ 1");
        assertThat(response.isCreator()).isTrue();
        verify(taskRepository).save(any(MeetingTask.class));
    }

    @Test
    void createTaskThrowsExceptionWhenMeetingNotMember() {
        when(meetingRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(new MeetingInfo()));
        when(personalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(false);

        CreateTaskRequest req = new CreateTaskRequest(
                "m1", null, 0, "Nhiệm vụ 1", "Mô tả", null, null, 0, 1, false, null
        );

        assertThatThrownBy(() -> taskService.createTask("user1", req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bạn không thuộc cuộc họp này");
    }

    @Test
    void updateTaskUpdatesFields() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        when(taskRepository.findOne(any(Specification.class))).thenReturn(Optional.of(task));

        UpdateTaskRequest req = new UpdateTaskRequest(
                "Nhiệm vụ sửa", "Mô tả mới", null, null, 1, 2, null, null, null
        );

        TaskListItemResponse response = taskService.updateTask("user1", "t1", req);

        assertThat(response.title()).isEqualTo("Nhiệm vụ sửa");
        assertThat(task.getStatus()).isEqualTo(1);
        verify(taskRepository).save(task);
    }

    @Test
    void updateTaskStatusUpdatesStatus() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        when(taskRepository.findOne(any(Specification.class))).thenReturn(Optional.of(task));

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(2);

        TaskListItemResponse response = taskService.updateTaskStatus("user1", "t1", req);

        assertThat(task.getStatus()).isEqualTo(2);
        verify(taskRepository).save(task);
    }

    @Test
    void deleteTaskDeletesTask() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        taskService.deleteTask("user1", "t1");

        verify(shareRepository).deleteByTaskId("t1");
        verify(taskRepository).delete(task);
    }

    @Test
    void setVisibilityUpdatesPublicFlag() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        taskService.setVisibility("user1", "t1", new TaskVisibilityRequest(true));

        assertThat(task.isPublicTask()).isTrue();
        verify(taskRepository).save(task);
    }

    @Test
    void addShareSavesTaskShare() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        AdAccount account = new AdAccount();
        account.setUserName("user2");
        account.setActive(true);

        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
        when(accountRepository.findById("user2")).thenReturn(Optional.of(account));

        TaskShareInputRequest req = new TaskShareInputRequest("user2", 1);

        TaskDetailResponse detail = taskService.addShare("user1", "t1", req);

        assertThat(detail.id()).isEqualTo("t1");
        verify(shareRepository).save(any(MeetingTaskShare.class));
    }

    @Test
    void removeShareDeletesShare() {
        MeetingTask task = sampleTask("t1", "user1", 0);
        MeetingTaskShare share = new MeetingTaskShare();
        share.setId("s1");
        share.setTaskId("t1");
        share.setUserName("user2");

        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));
        when(shareRepository.findByTaskIdAndUserName("t1", "user2")).thenReturn(Optional.of(share));

        taskService.removeShare("user1", "t1", "user2");

        verify(shareRepository).delete(share);
    }

    private static MeetingTask sampleTask(String id, String creator, int status) {
        MeetingTask t = new MeetingTask();
        t.setId(id);
        t.setTitle("Nhiệm vụ mẫu");
        t.setLevel(0);
        t.setStatus(status);
        t.setPriority(1);
        t.setPublicTask(false);
        t.setCreateBy(creator);
        t.setCreateDate(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        t.setUpdateBy(creator);
        t.setUpdateDate(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return t;
    }
}
