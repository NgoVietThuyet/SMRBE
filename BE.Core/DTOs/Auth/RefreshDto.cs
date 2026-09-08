namespace BE.Core.DTOs.Auth
{
    /// <summary>
    /// DTO chứa thông tin làm mới Access Token từ Refresh Token.
    /// </summary>
    public class RefreshDto
    {
        /// <summary>
        /// Chuỗi Refresh Token đã cấp trước đó dùng để làm mới.
        /// </summary>
        public string RefreshToken { get; set; }
    }
}
