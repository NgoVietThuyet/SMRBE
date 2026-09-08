//Lưu trữ thông tin chức danh.
namespace BE.Core.Entities.MD
{
    public class MdTitle : BaseEntity
    {
        public string Code { get; set; } // Khóa chính (PK) [cite: 112, 113]
        public string Name { get; set; } // Tên chức danh [cite: 113]
        public string Notes { get; set; } // Ghi chú [cite: 114]
        public int OrderNumber { get; set; } // Số thứ tự sắp xếp [cite: 115]
        public string? PermissionJson { get; set; }
        public bool IsActive { get; set; } = true;
    }
}
