namespace BE.Core.DTOs
{
    public class SendMeetingMessageDto
    {
        public string MeetingId { get; set; } = string.Empty;
        public string MessageText { get; set; } = string.Empty;
    }
}
