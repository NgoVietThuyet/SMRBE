namespace BE.Core.DTOs.Auth
{
    /// <summary>
    /// DTO chứa cấu hình đăng xuất tài khoản.
    /// </summary>
    public class LogoutDto
    {
        /// <summary>
        /// Kích hoạt thu hồi tất cả các phiên đang đăng nhập ở mọi thiết bị.
        /// </summary>
        public bool LogoutAllDevices { get; set; } = false;
    }
}
