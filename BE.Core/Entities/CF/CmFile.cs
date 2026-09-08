// Lưu metadata của các tệp tin được upload.
namespace BE.Core.Entities.CF
{
    public class CmFile : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK) 
        public string FileName { get; set; } // Tên tệp 
        public decimal FileSize { get; set; } // Kích thước tệp 
        public string MimeType { get; set; } // Kiểu MIME 
        public string Extention { get; set; } // Phần mở rộng 
        public int Type { get; set; } // Loại tệp 
        public string Icon { get; set; } // Biểu tượng hiển thị 
        public string RefrenceFileId { get; set; } // Khóa gom nhóm liên kết với MeetingInfo 
        public bool IsBienBan { get; set; } // Cờ xác định biên bản 
        public int OrderNumber { get; set; } // Thứ tự tệp (để ghép file ghi âm) 
        public string VoiceToText { get; set; } // Nội dung chuyển đổi giọng nói 
        public string BucketName { get; set; } = string.Empty;
        public string ObjectName { get; set; } = string.Empty;
    }
}
