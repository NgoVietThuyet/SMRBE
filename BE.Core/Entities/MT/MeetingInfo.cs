// Bảng trung tâm lưu trữ thông tin cuộc họp.
using System;

namespace BE.Core.Entities.MT
{
    public class MeetingInfo : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK) - Mã cuộc họp
        public string Name { get; set; } // Tên cuộc họp
        public DateTime ExpectedStartTime { get; set; } // Thời gian dự kiến bắt đầu
        public DateTime? StartDate { get; set; } // Thời gian bắt đầu thực tế
        public DateTime? EndDate { get; set; } // Thời gian kết thúc
        public string MeetContent { get; set; } // Nội dung cuộc họp
        public int Status { get; set; } // Trạng thái cuộc họp
        public string Notes { get; set; } // Ghi chú
        public string RefrenceFileId { get; set; } // Khóa gom nhóm tệp liên quan
        public DateTime? ExpectedEndTime { get; set; }
        public string TimeZone { get; set; } = "Asia/Bangkok";
        public string Agenda { get; set; } = string.Empty;
        public int Visibility { get; set; }
        public string RoomCode { get; set; } = string.Empty;
        public string JoinUrl { get; set; } = string.Empty;
        public string SettingsJson { get; set; } = "{\"schemaVersion\":1}";
        public string CancellationReason { get; set; } = string.Empty;
        public bool IsDraft { get; set; }
        public bool IsArchived { get; set; }
        public bool IsDeleted { get; set; }
        public int Version { get; set; } = 1;
        public byte[] RowVersion { get; set; } = Array.Empty<byte>();
    }
}
