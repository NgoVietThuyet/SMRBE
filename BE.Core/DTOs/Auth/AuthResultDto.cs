namespace BE.Core.DTOs.Auth
{
    /// <summary>
    /// DTO chứa kết quả trả về sau các thao tác xác thực và quản lý phiên.
    /// </summary>
    public class AuthResultDto
    {
        /// <summary>
        /// Cho biết tác vụ thành công hay thất bại.
        /// </summary>
        public bool Success { get; set; }

        /// <summary>
        /// Thông báo mô tả kết quả trả về.
        /// </summary>
        public string? Message { get; set; }

        /// <summary>
        /// Access Token dạng JWT dùng cho các API yêu cầu xác thực.
        /// </summary>
        public string? Token { get; set; }

        /// <summary>
        /// Tên đăng nhập của tài khoản xác thực.
        /// </summary>
        public string? UserName { get; set; }

        /// <summary>
        /// Họ và tên hiển thị đầy đủ.
        /// </summary>
        public string? FullName { get; set; }

        /// <summary>
        /// Email liên thụ của tài khoản.
        /// </summary>
        public string? Email { get; set; }

        /// <summary>
        /// Trạng thái yêu cầu đổi mật khẩu ở lần truy cập đầu tiên.
        /// </summary>
        public bool MustChangePassword { get; set; }

        /// <summary>
        /// Chuỗi Refresh Token cấp kèm theo dùng để xoay tua Access Token.
        /// </summary>
        public string? RefreshToken { get; set; }
    }
}
