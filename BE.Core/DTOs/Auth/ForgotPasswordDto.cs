namespace BE.Core.DTOs.Auth
{
    /// <summary>
    /// DTO yêu cầu gửi mã/token khôi phục mật khẩu.
    /// </summary>
    public class ForgotPasswordDto
    {
        /// <summary>
        /// Địa chỉ Email của tài khoản cần đổi mật khẩu.
        /// </summary>
        public string Email { get; set; }
    }
}
