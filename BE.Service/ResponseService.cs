using BE.Core.DTOs;

namespace BE.Service
{
    /// <summary>
    /// Tiện dịch ích tạo phản hồi chuẩn hóa dùng chung cho toàn hệ thống (gồm status, message, data).
    /// </summary>
    public static class ResponseService
    {
        /// <summary>
        /// Tạo đối tượng ResponseDto ở trạng thái thành công (Status = true).
        /// </summary>
        /// <param name="data">Dữ liệu trả về cho client.</param>
        /// <param name="message">Thông điệp thông báo.</param>
        /// <returns>Đối tượng ResponseDto được điền đầy đủ dữ liệu.</returns>
        public static ResponseDto Success(object? data = null, string message = "Thao tác thành công.")
        {
            return new ResponseDto
            {
                Status = true,
                Message = message,
                Data = data
            };
        }

        /// <summary>
        /// Tạo đối tượng ResponseDto ở trạng thái lỗi (Status = false).
        /// </summary>
        /// <param name="message">Thông điệp báo lỗi.</param>
        /// <param name="data">Dữ liệu bổ sung nếu có.</param>
        /// <returns>Đối tượng ResponseDto được điền đầy đủ dữ liệu lỗi.</returns>
        public static ResponseDto Fail(string message = "Thao tác thất bại.", object? data = null)
        {
            return new ResponseDto
            {
                Status = false,
                Message = message,
                Data = data
            };
        }
    }
}
