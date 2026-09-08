namespace BE.Core.DTOs.Auth
{
    /// <summary>
    /// DTO đăng ký phiên làm việc cho khách tham gia cuộc họp.
    /// </summary>
    public class GuestSessionDto
    {
        /// <summary>
        /// Mã định danh cuộc họp tham gia.
        /// </summary>
        public string MeetingId { get; set; }

        /// <summary>
        /// Tên hiển thị của khách trong phòng họp.
        /// </summary>
        public string DisplayName { get; set; }
    }
}
