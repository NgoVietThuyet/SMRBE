// Công việc của cuộc họp hoặc công việc riêng (không gắn cuộc họp).
using System;

namespace BE.Core.Entities.MT
{
    public class MeetingTask : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK) - Mã công việc
        public string? MeetingId { get; set; } // FK trỏ về MeetingInfo.Id, null nếu công việc riêng
        public string? ParentId { get; set; } // FK self -> MeetingTask.Id, null = Work lớn (Level 0)
        public int Level { get; set; } // 0=Work lớn, 1=SubWork, 2=Task con
        public string Title { get; set; } // Tên công việc
        public string? Description { get; set; } // Mô tả chi tiết
        public string? AssigneeUserName { get; set; } // Tên đăng nhập người phụ trách (không FK, như MeetingPersonal.UserName)
        public DateTime? DueDate { get; set; } // Thời hạn hoàn thành
        public int Status { get; set; } // Trạng thái: TaskStatus (NotStarted/InProgress/Completed)
        public int Priority { get; set; } // Ưu tiên: TaskPriority (Low/Medium/High)
        public bool IsPublic { get; set; } // Cờ công khai cho mọi người đăng nhập
        public string? SourceRef { get; set; } // Chứa Timestamp hoặc Transcript ID từ AI
        public MeetingInfo? Meeting { get; set; }
        public MeetingTask? Parent { get; set; }
        public ICollection<MeetingTask> Children { get; set; } = new List<MeetingTask>();
    }
}
