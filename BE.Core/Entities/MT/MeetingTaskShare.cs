// Người được chia sẻ một công việc, kèm quyền xem hoặc sửa.
using System;

namespace BE.Core.Entities.MT
{
    public class MeetingTaskShare : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK)
        public string TaskId { get; set; } // FK trỏ về MeetingTask.Id
        public string UserName { get; set; } // Tên đăng nhập người được chia sẻ
        public int Permission { get; set; } // Quyền: TaskSharePermission (View/Edit)
        public MeetingTask Task { get; set; }
    }
}
