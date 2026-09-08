// Lưu thông tin về phòng ban, vị trí 
namespace BE.Core.Entities.MD
{
    public class MdOrganize : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK) [cite: 108]
        public string PId { get; set; } // Khóa ngoại (FK) tạo cấu trúc cây phòng ban [cite: 109]
        public string Name { get; set; } // Tên đơn vị [cite: 110]
        public int OrderNumber { get; set; } // Số thứ tự sắp xếp [cite: 111]
        public bool Expanded { get; set; } // Trạng thái mở rộng trên giao diện [cite: 112]
        public string? PermissionJson { get; set; }
        public bool IsActive { get; set; } = true;
        public string? Notes { get; set; }
    }
}
