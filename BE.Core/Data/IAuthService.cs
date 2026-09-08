using BE.Core.DTOs.Auth;
using System.Threading.Tasks;

namespace BE.Core.Data
{
    /// <summary>
    /// Giao diện xử lý nghiệp vụ Xác thực và Quản lý phiên (AUTH).
    /// </summary>
    public interface IAuthService
    {
        /// <summary>
        /// Đăng ký tài khoản người dùng nội bộ mới.
        /// </summary>
        Task<AuthResultDto> RegisterAsync(RegisterDto dto);

        /// <summary>
        /// Đăng nhập tài khoản bằng Username/Email và Password.
        /// </summary>
        Task<AuthResultDto> LoginAsync(LoginDto dto);

        /// <summary>
        /// Đổi mật khẩu cá nhân cho người dùng đang đăng nhập.
        /// </summary>
        Task<AuthResultDto> ChangePasswordAsync(string userName, ChangePasswordDto dto);

        /// <summary>
        /// Làm mới Access Token thông qua xoay tua Refresh Token.
        /// </summary>
        Task<AuthResultDto> RefreshAsync(RefreshDto dto);

        /// <summary>
        /// Đăng xuất tài khoản khỏi phiên hiện tại hoặc toàn bộ các thiết bị.
        /// </summary>
        Task<AuthResultDto> LogoutAsync(string userName, LogoutDto dto);

        /// <summary>
        /// Khởi tạo yêu cầu quên mật khẩu, sinh token đặt lại.
        /// </summary>
        Task<AuthResultDto> ForgotPasswordAsync(ForgotPasswordDto dto);

        /// <summary>
        /// Thực thi đặt lại mật khẩu mới thông qua Token bảo mật.
        /// </summary>
        Task<AuthResultDto> ResetPasswordAsync(ResetPasswordDto dto);

        /// <summary>
        /// Cấp phiên truy cập và token tham gia cuộc họp cho khách vãng lai.
        /// </summary>
        Task<AuthResultDto> GuestSessionAsync(GuestSessionDto dto);
    }
}
