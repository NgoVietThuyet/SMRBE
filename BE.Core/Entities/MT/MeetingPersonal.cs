// Lưu trữ thành viên tham gia.
using System;

namespace BE.Core.Entities.MT
{
    public class MeetingPersonal : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK) 
        public string MeetingId { get; set; } // FK trỏ về MeetingInfo.Id 
        public string UserName { get; set; } // Tên đăng nhập người tham gia 
        public string FullName { get; set; } // Thông tin cá nhân 
        public string Phone { get; set; } // Thông tin cá nhân 
        public string Email { get; set; } // Thông tin cá nhân 
        public string Address { get; set; } // Thông tin cá nhân 
        public string OrgId { get; set; } // Mã đơn vị 
        public string TitleCode { get; set; } // FK trỏ về MdTitle.Code 
        public string RefrenceFileId { get; set; } // Mã tham chiếu file 
        public int Type { get; set; } // Loại người tham gia 
        public bool IsChuTri { get; set; } // Cờ xác định chủ trì 
        public bool IsJoined { get; set; } // Cờ xác định đã tham gia 
        public DateTime? JoinTime { get; set; } // Thời gian tham gia 
    }
}
