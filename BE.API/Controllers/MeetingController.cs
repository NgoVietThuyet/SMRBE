using BE.API.Hubs;
using BE.Core.Data;
using BE.Core.DTOs;
using BE.Service;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.SignalR;
using System;
using System.Threading.Tasks;

namespace BE.API.Controllers
{
    /// <summary>
    /// Controller điều phối các nghiệp vụ quản lý cuộc họp (MEET).
    /// </summary>
    [Authorize]
    [ApiController]
    [Route("api/[controller]")]
    public class MeetingController : ControllerBase
    {
        private readonly IMeetingService _service;
        private readonly IHubContext<MeetingHub> _hub;
        public MeetingController(IMeetingService service, IHubContext<MeetingHub> hub) { _service = service; _hub = hub; }
        private string CurrentUser => User.Identity?.Name ?? throw new UnauthorizedAccessException();

        [HttpGet("dashboard")]
        public Task<IActionResult> Dashboard(CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetDashboard(CurrentUser, ct))));

        [HttpPost("query")]
        public Task<IActionResult> Query([FromBody] MeetingSearchDto dto, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.SearchMeetings(dto, CurrentUser, ct))));

        [HttpGet("{meetingId}")]
        public Task<IActionResult> Detail(string meetingId, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetDetail(meetingId, CurrentUser, ct))));

        [HttpPost]
        public Task<IActionResult> Create([FromBody] CreateMeetingDto dto, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.CreateMeeting(dto, CurrentUser, ct), "Tạo cuộc họp thành công.")));

        [HttpPatch("{meetingId}")]
        public Task<IActionResult> Patch(string meetingId, [FromBody] UpdateMeetingDto dto, CancellationToken ct) => Execute(async () =>
        {
            dto.Id = meetingId;
            return Ok(ResponseService.Success(await _service.UpdateMeeting(dto, CurrentUser, ct), "Cập nhật cuộc họp thành công."));
        });

        [HttpDelete("{meetingId}")]
        public Task<IActionResult> Delete(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.DeleteDraft(meetingId, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Xóa bản nháp thành công."));
        });

        [HttpPost("{meetingId}/cancel")]
        public Task<IActionResult> CancelById(string meetingId, [FromBody] CancelMeetingDto dto, CancellationToken ct) => Execute(async () =>
        {
            dto.MeetingId = meetingId;
            await _service.CancelMeeting(dto, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Hủy cuộc họp thành công."));
        });

        [HttpPost("{meetingId}/archive")]
        public Task<IActionResult> Archive(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.ArchiveMeeting(meetingId, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Lưu trữ cuộc họp thành công."));
        });

        [HttpPost("{meetingId}/participants")]
        public Task<IActionResult> AddParticipantsById(string meetingId, [FromBody] UpdateMeetingParticipantsDto dto, CancellationToken ct) => Execute(async () =>
        {
            dto.MeetingId = meetingId;
            await _service.AddParticipants(dto, CurrentUser, ct);
            return Ok(ResponseService.Success(await _service.GetDetail(meetingId, CurrentUser, ct), "Thêm người tham gia thành công."));
        });

        [HttpDelete("{meetingId}/participants/{userName}")]
        public Task<IActionResult> RemoveParticipantById(string meetingId, string userName, CancellationToken ct) => Execute(async () =>
        {
            await _service.RemoveParticipant(new RemoveMeetingParticipantDto { MeetingId = meetingId, UserName = userName }, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Xóa người tham gia thành công."));
        });

        [HttpGet("{meetingId}/join-info")]
        public Task<IActionResult> JoinInfo(string meetingId, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetJoinInfo(meetingId, CurrentUser, ct))));

        [HttpPost("{meetingId}/start")]
        public Task<IActionResult> StartById(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.StartMeeting(meetingId, CurrentUser, ct);
            await _hub.Clients.Group(meetingId).SendAsync("MeetingStatusChanged", new { meetingId, status = 2, action = "started" }, ct);
            return Ok(ResponseService.Success(null, "Bắt đầu cuộc họp thành công."));
        });

        [HttpPost("{meetingId}/end")]
        public Task<IActionResult> EndById(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.EndMeeting(meetingId, CurrentUser, ct);
            await _hub.Clients.Group(meetingId).SendAsync("MeetingStatusChanged", new { meetingId, status = 3, action = "ended" }, ct);
            await _hub.Clients.Group(meetingId).SendAsync("PresenceChanged", new { meetingId, action = "ended" }, ct);
            return Ok(ResponseService.Success(null, "Kết thúc cuộc họp thành công."));
        });

        [HttpPost("quick")]
        public Task<IActionResult> CreateQuick([FromBody] QuickMeetingDto dto, CancellationToken ct) => Execute(async () =>
        {
            var meeting = await _service.CreateQuickMeeting(dto, CurrentUser, ct);
            return Ok(ResponseService.Success(meeting, "Tạo nhanh cuộc họp thành công."));
        });

        [HttpPost("{meetingId}/join")]
        public Task<IActionResult> Join(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.IntoTheMeeting(meetingId, CurrentUser, ct);
            await _hub.Clients.Group(meetingId).SendAsync("PresenceChanged", new { meetingId, userName = CurrentUser, action = "joined" }, ct);
            return Ok(ResponseService.Success(null, "Đã vào phòng họp."));
        });

        [HttpPost("{meetingId}/leave")]
        public Task<IActionResult> Leave(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.ExitTheMeeting(meetingId, CurrentUser, ct);
            await _hub.Clients.Group(meetingId).SendAsync("PresenceChanged", new { meetingId, userName = CurrentUser, action = "left" }, ct);
            return Ok(ResponseService.Success(null, "Đã rời phòng họp."));
        });

        [HttpGet("{meetingId}/messages")]
        public Task<IActionResult> GetMessages(string meetingId, CancellationToken ct) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetMessages(meetingId, CurrentUser, ct))));

        [HttpPost("{meetingId}/messages")]
        public Task<IActionResult> SendMessage(string meetingId, [FromBody] SendMeetingMessageDto dto, CancellationToken ct) => Execute(async () =>
        {
            var msg = await _service.SendMessage(meetingId, CurrentUser, dto.MessageText, ct);
            await _hub.Clients.Group(meetingId).SendAsync("ReceiveMessage", msg, ct);
            return Ok(ResponseService.Success(msg));
        });

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
