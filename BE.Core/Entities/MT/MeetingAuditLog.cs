namespace BE.Core.Entities.MT;

public class MeetingAuditLog
{
    public string Id { get; set; } = string.Empty;
    public string MeetingId { get; set; } = string.Empty;
    public string Action { get; set; } = string.Empty;
    public string ActorId { get; set; } = string.Empty;
    public DateTime OccurredAt { get; set; }
    public string CorrelationId { get; set; } = string.Empty;
    public int Version { get; set; }
    public string PayloadJson { get; set; } = string.Empty;
}
