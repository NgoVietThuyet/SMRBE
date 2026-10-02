package vn.com.d2s.smr.controller.mt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.com.d2s.smr.controller.common.GlobalExceptionHandler;
import vn.com.d2s.smr.dto.mt.task.CreateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.TaskDetailResponse;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;
import vn.com.d2s.smr.dto.mt.task.TaskSearchRequest;
import vn.com.d2s.smr.dto.mt.task.TaskSearchResultResponse;
import vn.com.d2s.smr.dto.mt.task.TaskShareInputRequest;
import vn.com.d2s.smr.dto.mt.task.TaskSummaryResponse;
import vn.com.d2s.smr.dto.mt.task.TaskVisibilityRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskStatusRequest;
import vn.com.d2s.smr.service.mt.TaskService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock
    private TaskService taskService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void queryReturns200WithSearchResult() throws Exception {
        TaskListItemResponse item = sampleItem("t1");
        TaskSearchResultResponse result = new TaskSearchResultResponse(
                List.of(item), 1, 1, 1, 10, new TaskSummaryResponse(1, 1, 0, 0)
        );
        when(taskService.searchTasks(eq("user1"), any())).thenReturn(result);

        TaskSearchRequest req = new TaskSearchRequest("all", "", null, null, null, null, null, null, 1, 10);

        mockMvc.perform(post("/api/Task/query")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value("t1"));
    }

    @Test
    void detailReturns200() throws Exception {
        TaskDetailResponse detail = sampleDetail("t1");
        when(taskService.getTask("user1", "t1")).thenReturn(detail);

        mockMvc.perform(get("/api/Task/t1").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.id").value("t1"));
    }

    @Test
    void createReturns200() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest(
                null, null, 0, "Task A", "Desc", null, null, 0, 1, false, null
        );
        TaskListItemResponse item = sampleItem("t1");
        when(taskService.createTask(eq("user1"), any())).thenReturn(item);

        mockMvc.perform(post("/api/Task")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Tạo công việc thành công."));
    }

    @Test
    void updateReturns200() throws Exception {
        UpdateTaskRequest req = new UpdateTaskRequest(
                "Task A Updated", "Desc", null, null, 1, 1, null, null, null
        );
        TaskListItemResponse item = sampleItem("t1");
        when(taskService.updateTask(eq("user1"), eq("t1"), any())).thenReturn(item);

        mockMvc.perform(put("/api/Task/t1")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật công việc thành công."));
    }

    @Test
    void updateStatusReturns200() throws Exception {
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(2);
        TaskListItemResponse item = sampleItem("t1");
        when(taskService.updateTaskStatus(eq("user1"), eq("t1"), any())).thenReturn(item);

        mockMvc.perform(patch("/api/Task/t1/status")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái công việc thành công."));
    }

    @Test
    void deleteReturns200() throws Exception {
        TaskListItemResponse item = sampleItem("t1");
        when(taskService.deleteTask("user1", "t1")).thenReturn(item);

        mockMvc.perform(delete("/api/Task/t1").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Xóa công việc thành công."));
    }

    @Test
    void setVisibilityReturns200() throws Exception {
        TaskVisibilityRequest req = new TaskVisibilityRequest(true);

        mockMvc.perform(put("/api/Task/t1/visibility")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật quyền công khai thành công."));

        verify(taskService).setVisibility(eq("user1"), eq("t1"), any());
    }

    @Test
    void addShareReturns200() throws Exception {
        TaskShareInputRequest req = new TaskShareInputRequest("user2", 1);
        TaskDetailResponse detail = sampleDetail("t1");
        when(taskService.addShare(eq("user1"), eq("t1"), any())).thenReturn(detail);

        mockMvc.perform(post("/api/Task/t1/shares")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Chia sẻ công việc thành công."));
    }

    @Test
    void removeShareReturns200() throws Exception {
        mockMvc.perform(delete("/api/Task/t1/shares/user2").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Xóa người được chia sẻ thành công."));

        verify(taskService).removeShare("user1", "t1", "user2");
    }

    private static TaskListItemResponse sampleItem(String id) {
        return new TaskListItemResponse(
                id, null, null, null, null, 0, 0, 0, 0,
                "Task A", "Desc", "user1", "User One",
                LocalDateTime.now(), 0, 1, false, true, true,
                "user1", LocalDateTime.now(), LocalDateTime.now(), null
        );
    }

    private static TaskDetailResponse sampleDetail(String id) {
        return new TaskDetailResponse(
                id, null, null, null, null, 0, 0, 0, 0,
                "Task A", "Desc", "user1", "User One",
                LocalDateTime.now(), 0, 1, false, true, true,
                "user1", LocalDateTime.now(), LocalDateTime.now(), null,
                List.of(), List.of()
        );
    }
}
