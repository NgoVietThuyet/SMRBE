// Lưu lịch sử tin nhắn trong cuộc họp.
namespace BE.Core.Entities.MT
{
    public class MeetingMessage : BaseEntity
    {
        public string Id { get; set; } // Khóa chính (PK) 
        public string MeetingId { get; set; } // FK trỏ về MeetingInfo.Id 
        public string SenderUserId { get; set; } // Định danh người gửi 
        public string ReceiverUserId { get; set; } // Định danh người nhận 
        public string MessageText { get; set; } // Nội dung tin nhắn 
    }
}
