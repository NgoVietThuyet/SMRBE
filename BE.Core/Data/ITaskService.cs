using BE.Core.DTOs;

namespace BE.Core.Data;

public interface ITaskService
{
    Task<TaskSearchResultDto> SearchTasks(TaskSearchDto dto, string userName, CancellationToken ct = default);
    Task<TaskDetailDto> GetTask(string taskId, string userName, CancellationToken ct = default);
    Task<TaskListItemDto> CreateTask(CreateTaskDto dto, string creatorUserName, CancellationToken ct = default);
    Task<TaskListItemDto> UpdateTask(string taskId, UpdateTaskDto dto, string userName, CancellationToken ct = default);
    Task<TaskListItemDto> DeleteTask(string taskId, string userName, CancellationToken ct = default);
    Task SetVisibility(string taskId, TaskVisibilityDto dto, string userName, CancellationToken ct = default);
    Task<TaskDetailDto> AddShare(string taskId, TaskShareInputDto dto, string userName, CancellationToken ct = default);
    Task RemoveShare(string taskId, string targetUserName, string userName, CancellationToken ct = default);
    Task<TaskListItemDto> UpdateTaskStatus(string taskId, UpdateTaskStatusDto dto, string userName, CancellationToken ct = default);
}
