using BE.Core.Data;
using BE.Core.DTOs;
using BE.Service;
using BE.API.Hubs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;
using System;
using System.Threading.Tasks;

namespace BE.API.Controllers
{
    /// <summary>
    /// Controller điều phối các nghiệp vụ quản lý công việc (TASK).
    /// </summary>
    [Authorize]
    [ApiController]
    [Route("api/[controller]")]
    public class TaskController : ControllerBase
    {
        private readonly ITaskService _service;
        private readonly IHubContext<MeetingHub> _hub;
        public TaskController(ITaskService service, IHubContext<MeetingHub> hub)
        {
            _service = service;
            _hub = hub;
        }
        private string CurrentUser => User.Identity?.Name ?? throw new UnauthorizedAccessException();

        [HttpPost("query")]
        public Task<IActionResult> Query([FromBody] TaskSearchDto dto, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.SearchTasks(dto, CurrentUser, ct))));

        [HttpGet("{taskId}")]
        public Task<IActionResult> Detail(string taskId, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetTask(taskId, CurrentUser, ct))));

        [HttpPost]
        public Task<IActionResult> Create([FromBody] CreateTaskDto dto, CancellationToken ct) => Execute(async () =>
        {
            var task = await _service.CreateTask(dto, CurrentUser, ct);
            await NotifyTaskChanged(task, "created", ct);
            return Ok(ResponseService.Success(task, "Tạo công việc thành công."));
        });

        [HttpPut("{taskId}")]
        public Task<IActionResult> Update(string taskId, [FromBody] UpdateTaskDto dto, CancellationToken ct) => Execute(async () =>
        {
            var task = await _service.UpdateTask(taskId, dto, CurrentUser, ct);
            await NotifyTaskChanged(task, "updated", ct);
            return Ok(ResponseService.Success(task, "Cập nhật công việc thành công."));
        });

        [HttpPatch("{taskId}/status")]
        public Task<IActionResult> UpdateStatus(string taskId, [FromBody] UpdateTaskStatusDto dto, CancellationToken ct) => Execute(async () =>
        {
            var task = await _service.UpdateTaskStatus(taskId, dto, CurrentUser, ct);
            await NotifyTaskChanged(task, "status_updated", ct);
            return Ok(ResponseService.Success(task, "Cập nhật trạng thái công việc thành công."));
        });

        [HttpDelete("{taskId}")]
        public Task<IActionResult> Delete(string taskId, CancellationToken ct) => Execute(async () =>
        {
            var task = await _service.DeleteTask(taskId, CurrentUser, ct);
            await NotifyTaskChanged(task, "deleted", ct);
            return Ok(ResponseService.Success(null, "Xóa công việc thành công."));
        });

        [HttpPut("{taskId}/visibility")]
        public Task<IActionResult> SetVisibility(string taskId, [FromBody] TaskVisibilityDto dto, CancellationToken ct) => Execute(async () =>
        {
            await _service.SetVisibility(taskId, dto, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Cập nhật quyền công khai thành công."));
        });

        [HttpPost("{taskId}/shares")]
        public Task<IActionResult> AddShare(string taskId, [FromBody] TaskShareInputDto dto, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.AddShare(taskId, dto, CurrentUser, ct), "Chia sẻ công việc thành công.")));

        [HttpDelete("{taskId}/shares/{userName}")]
        public Task<IActionResult> RemoveShare(string taskId, string userName, CancellationToken ct) => Execute(async () =>
        {
            await _service.RemoveShare(taskId, userName, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Xóa người được chia sẻ thành công."));
        });

        // Broadcast TaskChanged tới nhóm cuộc họp (nhóm hub keyed theo meetingId).
        // Chỉ task gắn cuộc họp mới có nhóm để gửi; thay đổi share/public không broadcast
        // vì không ảnh hưởng hiển thị của thành viên họp (họ luôn xem được task của họp mình).
        private async Task NotifyTaskChanged(TaskListItemDto task, string action, CancellationToken ct)
        {
            if (string.IsNullOrEmpty(task.MeetingId)) return;
            await _hub.Clients.Group(task.MeetingId).SendAsync("TaskChanged",
                new { meetingId = task.MeetingId, taskId = task.Id, action, userName = CurrentUser }, ct);
        }

        private static async Task<IActionResult> Execute(Func<Task<IActionResult>> action)
        {
            try
            {
                return await action();
            }
            catch (ArgumentException ex)
            {
                return new BadRequestObjectResult(ResponseService.Fail(ex.Message));
            }
            catch (InvalidOperationException ex)
            {
                return new ConflictObjectResult(ResponseService.Fail(ex.Message));
            }
            catch (UnauthorizedAccessException)
            {
                return new ObjectResult(ResponseService.Fail("Bạn không có quyền thực hiện thao tác này.")) { StatusCode = 403 };
            }
            catch (KeyNotFoundException ex)
            {
                return new NotFoundObjectResult(ResponseService.Fail(ex.Message));
            }
            catch (Exception ex)
            {
                return new ObjectResult(ResponseService.Fail($"Lỗi máy chủ nội bộ: {ex.Message}")) { StatusCode = 500 };
            }
        }
    }
}
