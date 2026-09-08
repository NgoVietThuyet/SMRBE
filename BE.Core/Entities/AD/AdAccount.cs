namespace BE.Core.Entities.AD
{
    public class AdAccount : BaseEntity
    {
        public string UserName { get; set; } // Khóa chính (PK)
        public string Password { get; set; } // Mật khẩu
        public string FullName { get; set; } // Họ và tên
        public string Phone { get; set; } // Số điện thoại
        public string Email { get; set; } // Địa chỉ email
        public string Address { get; set; } // Địa chỉ
        public string OrgId { get; set; } // FK liên kết với MdOrganize.Id
        public string TitleCode { get; set; } // FK liên kết với MdTitle.Code
        public string? PermissionJson { get; set; }
        public string? RoleCodes { get; set; }
        public bool IsActive { get; set; } = true;
        public bool MustChangePassword { get; set; } = true;
        public DateTime? LastLoginAt { get; set; }
        public int TokenVersion { get; set; } = 1;
    }
}
