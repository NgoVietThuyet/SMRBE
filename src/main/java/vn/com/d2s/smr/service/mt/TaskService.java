package vn.com.d2s.smr.service.mt;

import vn.com.d2s.smr.dto.mt.task.CreateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.TaskDetailResponse;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;
import vn.com.d2s.smr.dto.mt.task.TaskSearchRequest;
import vn.com.d2s.smr.dto.mt.task.TaskSearchResultResponse;
import vn.com.d2s.smr.dto.mt.task.TaskShareInputRequest;
import vn.com.d2s.smr.dto.mt.task.TaskVisibilityRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskStatusRequest;

public interface TaskService {

    TaskSearchResultResponse searchTasks(String userName, TaskSearchRequest request);

    TaskDetailResponse getTask(String userName, String taskId);

    TaskListItemResponse createTask(String creatorUserName, CreateTaskRequest request);

    TaskListItemResponse updateTask(String userName, String taskId, UpdateTaskRequest request);

    TaskListItemResponse deleteTask(String userName, String taskId);

    TaskListItemResponse updateTaskStatus(String userName, String taskId, UpdateTaskStatusRequest request);

    void setVisibility(String userName, String taskId, TaskVisibilityRequest request);

    TaskDetailResponse addShare(String userName, String taskId, TaskShareInputRequest request);

    void removeShare(String userName, String taskId, String targetUserName);
}
