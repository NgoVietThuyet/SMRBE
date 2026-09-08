using BE.Core.Data;
using BE.Core.DTOs;
using BE.Service;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
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
        public MeetingController(IMeetingService service) => _service = service;
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

        [HttpPost("{meetingId}/start")]
        public Task<IActionResult> StartById(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.StartMeeting(meetingId, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Bắt đầu cuộc họp thành công."));
        });

        [HttpPost("{meetingId}/end")]
        public Task<IActionResult> EndById(string meetingId, CancellationToken ct) => Execute(async () =>
        {
            await _service.EndMeeting(meetingId, CurrentUser, ct);
            return Ok(ResponseService.Success(null, "Kết thúc cuộc họp thành công."));
        });

        /// <summary>
        /// Lấy tất cả danh sách cuộc họp của người dùng hiện tại (bản cũ).
        /// </summary>
        [HttpGet("GetMeetings")]
        public Task<IActionResult> GetMeetings() => Execute(async () => Ok(ResponseService.Success(await _service.GetMeetings(CurrentUser))));

        /// <summary>
        /// Tìm kiếm cuộc họp nâng cao, hỗ trợ phân trang và phân nhóm Tab.
        /// </summary>
        [HttpPost("Search")]
        public Task<IActionResult> Search([FromBody] MeetingSearchDto dto) => Execute(async () =>
        {
            var result = await _service.SearchMeetings(dto, CurrentUser);
            return Ok(ResponseService.Success(result, "Tìm kiếm cuộc họp thành công."));
        });

        /// <summary>
        /// Lấy thông tin chi tiết của một cuộc họp cụ thể.
        /// </summary>
        [HttpGet("GetInfoMeeting/{meetingId}")]
        public Task<IActionResult> GetInfoMeeting(string meetingId) => Execute(async () =>
            await _service.GetInfoMeeting(meetingId, CurrentUser) is { } meeting
                ? Ok(ResponseService.Success(meeting, "Lấy thông tin chi tiết cuộc họp thành công."))
                : NotFound(ResponseService.Fail("Không tìm thấy cuộc họp hoặc bạn không có quyền xem.")));

        /// <summary>
        /// Lấy danh sách những người tham gia vào cuộc họp.
        /// </summary>
        [HttpGet("GetPersonalMeeting/{meetingId}")]
        public Task<IActionResult> GetPersonalMeeting(string meetingId) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetPersonalMeeting(meetingId, CurrentUser))));

        /// <summary>
        /// Tạo cuộc họp mới theo lịch dự kiến.
        /// </summary>
        [HttpPost("CreateMeeting")]
        public Task<IActionResult> CreateMeeting([FromBody] CreateMeetingDto dto) => Execute(async () =>
        {
            var meeting = await _service.CreateMeeting(dto, CurrentUser);
            return Ok(ResponseService.Success(meeting, "Tạo cuộc họp thành công."));
        });

        /// <summary>
        /// Tạo nhanh cuộc họp mới bắt đầu ngay lập tức.
        /// </summary>
        [HttpPost("Quick")]
        public Task<IActionResult> CreateQuick([FromBody] QuickMeetingDto dto) => Execute(async () =>
        {
            var meeting = await _service.CreateQuickMeeting(dto, CurrentUser);
            return Ok(ResponseService.Success(meeting, "Tạo nhanh cuộc họp thành công."));
        });

        /// <summary>
        /// Cập nhật thông tin chi tiết cuộc họp (chỉ chủ trì được phép).
        /// </summary>
        [HttpPost("Update")]
        public Task<IActionResult> Update([FromBody] UpdateMeetingDto dto) => Execute(async () =>
        {
            var meeting = await _service.UpdateMeeting(dto, CurrentUser);
            return Ok(ResponseService.Success(meeting, "Cập nhật cuộc họp thành công."));
        });

        /// <summary>
        /// Hủy cuộc họp chưa diễn ra kèm lý do.
        /// </summary>
        [HttpPost("Cancel")]
        public Task<IActionResult> Cancel([FromBody] CancelMeetingDto dto) => Execute(async () =>
        {
            await _service.CancelMeeting(dto, CurrentUser);
            return Ok(ResponseService.Success(null, "Hủy cuộc họp thành công."));
        });

        /// <summary>
        /// Thêm thành phần tham gia cuộc họp.
        /// </summary>
        [HttpPost("AddParticipants")]
        public Task<IActionResult> AddParticipants([FromBody] UpdateMeetingParticipantsDto dto) => Execute(async () =>
        {
            await _service.AddParticipants(dto, CurrentUser);
            return Ok(ResponseService.Success(await _service.GetPersonalMeeting(dto.MeetingId, CurrentUser), "Thêm thành viên thành công."));
        });

        /// <summary>
        /// Xóa người tham gia khỏi cuộc họp.
        /// </summary>
        [HttpDelete("RemoveParticipant")]
        public Task<IActionResult> RemoveParticipant([FromBody] RemoveMeetingParticipantDto dto) => Execute(async () =>
        {
            await _service.RemoveParticipant(dto, CurrentUser);
            return Ok(ResponseService.Success(null, "Xóa thành viên thành công."));
        });

        /// <summary>
        /// Bắt đầu tiến hành cuộc họp (Chuyển trạng thái sang đang diễn ra).
        /// </summary>
        [HttpPost("StartMeeting")]
        public Task<IActionResult> StartMeeting([FromBody] MeetingActionDto dto) => Execute(async () =>
        {
            await _service.StartMeeting(dto.MeetingId, CurrentUser);
            return Ok(ResponseService.Success(null, "Bắt đầu cuộc họp thành công."));
        });

        /// <summary>
        /// Kết thúc cuộc họp (Chuyển trạng thái sang đã kết thúc).
        /// </summary>
        [HttpPost("EndMeeting")]
        public Task<IActionResult> EndMeeting([FromBody] MeetingActionDto dto) => Execute(async () =>
        {
            await _service.EndMeeting(dto.MeetingId, CurrentUser);
            return Ok(ResponseService.Success(null, "Kết thúc cuộc họp thành công."));
        });

        /// <summary>
        /// Người tham dự tiến vào phòng họp.
        /// </summary>
        [HttpPost("IntoTheMeeting")]
        public Task<IActionResult> IntoTheMeeting([FromBody] MeetingActionDto dto) => Execute(async () =>
        {
            await _service.IntoTheMeeting(dto.MeetingId, CurrentUser);
            return Ok(ResponseService.Success(null, "Đã vào phòng họp."));
        });

        /// <summary>
        /// Người tham dự rời khỏi phòng họp.
        /// </summary>
        [HttpPost("ExitTheMeeting")]
        public Task<IActionResult> ExitTheMeeting([FromBody] MeetingActionDto dto) => Execute(async () =>
        {
            await _service.ExitTheMeeting(dto.MeetingId, CurrentUser);
            return Ok(ResponseService.Success(null, "Đã rời phòng họp."));
        });

        /// <summary>
        /// Lấy danh sách tin nhắn chat của cuộc họp.
        /// </summary>
        [HttpGet("GetMessages/{meetingId}")]
        public Task<IActionResult> GetMessages(string meetingId) => Execute(async () =>
            Ok(ResponseService.Success(await _service.GetMessages(meetingId, CurrentUser))));

        /// <summary>
        /// Gửi tin nhắn chat vào phòng họp.
        /// </summary>
        [HttpPost("SendMessage")]
        public Task<IActionResult> SendMessage([FromBody] SendMeetingMessageDto dto) => Execute(async () =>
            Ok(ResponseService.Success(await _service.SendMessage(dto.MeetingId, CurrentUser, dto.MessageText))));

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
