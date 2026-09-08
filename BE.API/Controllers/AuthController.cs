using BE.Core.Data;
using BE.Core.DTOs.Auth;
using BE.Service;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using System.Security.Claims;
using System.Threading.Tasks;
using Microsoft.Extensions.Configuration;

namespace BE.API.Controllers
{
    /// <summary>
    /// Controller xử lý các yêu cầu Xác thực và Quản lý phiên (AUTH).
    /// </summary>
    [ApiController]
    [Route("api/[controller]")]
    public class AuthController : ControllerBase
    {
        private readonly IAuthService _authService;
        private readonly IConfiguration _configuration;

        /// <summary>
        /// Khởi tạo AuthController với AuthService và Configuration.
        /// </summary>
        public AuthController(IAuthService authService, IConfiguration configuration)
        {
            _authService = authService;
            _configuration = configuration;
        }

        /// <summary>
        /// Đăng ký tài khoản người dùng mới.
        /// </summary>
        [HttpPost("Register")]
        public async Task<IActionResult> Register([FromBody] RegisterDto dto)
        {
            if (!_configuration.GetValue("Features:PublicRegistration", false))
                return NotFound(ResponseService.Fail("Hệ thống không kích hoạt tính năng đăng ký công khai."));

            if (string.IsNullOrWhiteSpace(dto.UserName) || string.IsNullOrWhiteSpace(dto.Password))
                return BadRequest(ResponseService.Fail("Tên đăng nhập và mật khẩu không được để trống."));

            var result = await _authService.RegisterAsync(dto);
            return result.Success
                ? Ok(ResponseService.Success(result, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Đăng nhập tài khoản bằng Tên đăng nhập/Email và Mật khẩu.
        /// </summary>
        [HttpPost("Login")]
        public async Task<IActionResult> Login([FromBody] LoginDto dto)
        {
            if (string.IsNullOrWhiteSpace(dto.UserName) || string.IsNullOrWhiteSpace(dto.Password))
                return BadRequest(ResponseService.Fail("Vui lòng nhập đầy đủ thông tin Tên đăng nhập và Mật khẩu."));

            var result = await _authService.LoginAsync(dto);
            return result.Success
                ? Ok(ResponseService.Success(result, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Đổi mật khẩu cá nhân cho tài khoản đang truy cập.
        /// </summary>
        [Authorize]
        [HttpPost("ChangePassword")]
        public async Task<IActionResult> ChangePassword([FromBody] ChangePasswordDto dto)
        {
            if (string.IsNullOrWhiteSpace(dto.CurrentPassword) || string.IsNullOrWhiteSpace(dto.NewPassword))
                return BadRequest(ResponseService.Fail("Vui lòng nhập đầy đủ mật khẩu hiện tại và mật khẩu mới."));

            var result = await _authService.ChangePasswordAsync(User.FindFirstValue(ClaimTypes.Name)!, dto);
            return result.Success
                ? Ok(ResponseService.Success(null, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Xoay tua Access Token từ Refresh Token.
        /// </summary>
        [HttpPost("Refresh")]
        public async Task<IActionResult> Refresh([FromBody] RefreshDto dto)
        {
            if (dto == null || string.IsNullOrWhiteSpace(dto.RefreshToken))
                return BadRequest(ResponseService.Fail("Vui lòng cung cấp Refresh Token hợp lệ."));

            var result = await _authService.RefreshAsync(dto);
            return result.Success
                ? Ok(ResponseService.Success(result, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Đăng xuất phiên hiện tại hoặc toàn bộ các thiết bị.
        /// </summary>
        [Authorize]
        [HttpPost("Logout")]
        public async Task<IActionResult> Logout([FromBody] LogoutDto dto)
        {
            var userName = User.FindFirstValue(ClaimTypes.Name);
            if (string.IsNullOrEmpty(userName)) return Unauthorized(ResponseService.Fail("Vui lòng đăng nhập để thực hiện tác vụ."));

            var result = await _authService.LogoutAsync(userName, dto);
            return result.Success
                ? Ok(ResponseService.Success(null, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Gửi yêu cầu đặt lại mật khẩu của người dùng qua Email.
        /// </summary>
        [HttpPost("ForgotPassword")]
        public async Task<IActionResult> ForgotPassword([FromBody] ForgotPasswordDto dto)
        {
            if (dto == null || string.IsNullOrWhiteSpace(dto.Email))
                return BadRequest(ResponseService.Fail("Vui lòng nhập địa chỉ Email hợp lệ."));

            var result = await _authService.ForgotPasswordAsync(dto);
            return result.Success
                ? Ok(ResponseService.Success(result.Token, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Đặt lại mật khẩu mới cho tài khoản qua Token bảo mật.
        /// </summary>
        [HttpPost("ResetPassword")]
        public async Task<IActionResult> ResetPassword([FromBody] ResetPasswordDto dto)
        {
            if (dto == null || string.IsNullOrWhiteSpace(dto.ResetToken) || string.IsNullOrWhiteSpace(dto.NewPassword))
                return BadRequest(ResponseService.Fail("Vui lòng nhập đầy đủ thông tin ResetToken và mật khẩu mới."));

            var result = await _authService.ResetPasswordAsync(dto);
            return result.Success
                ? Ok(ResponseService.Success(null, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }

        /// <summary>
        /// Trực tiếp khởi tạo phiên khách để tham gia cuộc họp.
        /// </summary>
        [HttpPost("GuestSession")]
        public async Task<IActionResult> GuestSession([FromBody] GuestSessionDto dto)
        {
            if (dto == null || string.IsNullOrWhiteSpace(dto.MeetingId) || string.IsNullOrWhiteSpace(dto.DisplayName))
                return BadRequest(ResponseService.Fail("Vui lòng nhập mã cuộc họp và tên hiển thị khách."));

            var result = await _authService.GuestSessionAsync(dto);
            return result.Success
                ? Ok(ResponseService.Success(result, result.Message))
                : BadRequest(ResponseService.Fail(result.Message));
        }
    }
}
