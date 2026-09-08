namespace BE.Core.DTOs.Auth
{
    /// <summary>
    /// DTO thực thi đặt lại mật khẩu mới thông qua Token bảo mật.
    /// </summary>
    public class ResetPasswordDto
    {
        /// <summary>
        /// Token khôi phục mật khẩu hợp lệ đã được gửi qua email.
        /// </summary>
        public string ResetToken { get; set; }

        /// <summary>
        /// Mật khẩu mới cần thiết lập.
        /// </summary>
        public string NewPassword { get; set; }
    }
}
