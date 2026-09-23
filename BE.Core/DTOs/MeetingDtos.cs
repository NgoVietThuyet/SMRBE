using System.ComponentModel.DataAnnotations;

namespace BE.Core.DTOs;

public enum MeetingStatus { Draft = 0, Scheduled = 1, Ongoing = 2, Ended = 3, Cancelled = 4, Archived = 5 }
public enum MeetingVisibility { InvitedOnly = 0, Internal = 1, Public = 2 }
public enum MeetingParticipantRole { Host = 1, CoHost = 2, Secretary = 3, Required = 4, Optional = 5 }

public sealed class MeetingSettingsDto
{
    public int SchemaVersion { get; set; } = 1;
    public bool LobbyEnabled { get; set; }
    public bool AllowGuests { get; set; }
    public bool AllowJoinBeforeHost { get; set; }
    public bool ChatEnabled { get; set; } = true;
    public bool ScreenShareEnabled { get; set; } = true;
    public bool WhiteboardEnabled { get; set; } = true;
    public bool FileUploadEnabled { get; set; } = true;
    public bool RecordingEnabled { get; set; } = true;
    public bool CaptionsEnabled { get; set; }
    public bool AiMinutesEnabled { get; set; } = true;
    public string MinutesTemplateId { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    // Cờ FE gửi lên khi bật "Yêu cầu mật khẩu" (JSON camelCase: hasPassword).
    // Lưu độc lập với PasswordHash để việc xóa hash khi trả về client
    // (trong ToDetail) không làm mất cờ này.
    public bool HasPassword { get; set; }
}

public sealed class MeetingParticipantInputDto
{
    [Required] public string UserName { get; set; } = string.Empty;
    public MeetingParticipantRole Role { get; set; } = MeetingParticipantRole.Required;
}

public class CreateMeetingDto
{
    [Required, StringLength(200)] public string Name { get; set; } = string.Empty;
    [StringLength(4000)] public string Description { get; set; } = string.Empty;
    [StringLength(8000)] public string Agenda { get; set; } = string.Empty;
    public DateTime ExpectedStartTime { get; set; }
    public DateTime? ExpectedEndTime { get; set; }
    [StringLength(100)] public string TimeZone { get; set; } = "Asia/Bangkok";
    public MeetingVisibility Visibility { get; set; } = MeetingVisibility.InvitedOnly;
    public bool SaveAsDraft { get; set; }
    public bool PublishInvitation { get; set; } = true;
    public MeetingSettingsDto Settings { get; set; } = new();
    public List<MeetingParticipantInputDto> Participants { get; set; } = new();
    public List<string> ParticipantUserNames { get; set; } = new();
}

public sealed class MeetingSearchDto
{
    public string Tab { get; set; } = "all";
    public string Keyword { get; set; } = string.Empty;
    public MeetingStatus? Status { get; set; }
    public string? OrganizationId { get; set; }
    public int Page { get; set; } = 1;
    public int PageSize { get; set; } = 10;
    public DateTime? StartDate { get; set; }
    public DateTime? EndDate { get; set; }
}

public sealed class PagedResultDto<T>
{
    public List<T> Items { get; set; } = new();
    public int TotalItems { get; set; }
    public int TotalPages { get; set; }
    public int Page { get; set; }
    public int PageSize { get; set; }
}

public sealed class QuickMeetingDto
{
    [StringLength(200)] public string Name { get; set; } = string.Empty;
    public List<string> ParticipantUserNames { get; set; } = new();
}

public sealed class UpdateMeetingDto
{
    [Required] public string Id { get; set; } = string.Empty;
    [Required, StringLength(200)] public string Name { get; set; } = string.Empty;
    [StringLength(4000)] public string Description { get; set; } = string.Empty;
    [StringLength(8000)] public string Agenda { get; set; } = string.Empty;
    public DateTime ExpectedStartTime { get; set; }
    public DateTime? ExpectedEndTime { get; set; }
    [StringLength(100)] public string TimeZone { get; set; } = "Asia/Bangkok";
    public MeetingVisibility Visibility { get; set; }
    public MeetingSettingsDto Settings { get; set; } = new();
    public string RowVersion { get; set; } = string.Empty;
    public string MeetContent { get => Description; set => Description = value; }
    public string Notes { get; set; } = string.Empty;
}

public sealed class CancelMeetingDto
{
    [Required] public string MeetingId { get; set; } = string.Empty;
    [Required, StringLength(1000)] public string Reason { get; set; } = string.Empty;
}

public sealed class UpdateMeetingParticipantsDto
{
    [Required] public string MeetingId { get; set; } = string.Empty;
    public List<MeetingParticipantInputDto> Participants { get; set; } = new();
    public List<string> UserNames { get; set; } = new();
}

public sealed class RemoveMeetingParticipantDto
{
    [Required] public string MeetingId { get; set; } = string.Empty;
    [Required] public string UserName { get; set; } = string.Empty;
}

public class MeetingListItemDto
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public DateTime ExpectedStartTime { get; set; }
    public DateTime? ExpectedEndTime { get; set; }
    public MeetingStatus Status { get; set; }
    public MeetingVisibility Visibility { get; set; }
    public string RoomCode { get; set; } = string.Empty;
    public string JoinUrl { get; set; } = string.Empty;
    public int ParticipantCount { get; set; }
    public bool IsHost { get; set; }
    public string HostName { get; set; } = string.Empty;
}

public sealed class MeetingParticipantDto
{
    public string UserName { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string OrganizationId { get; set; } = string.Empty;
    public string TitleCode { get; set; } = string.Empty;
    public MeetingParticipantRole Role { get; set; }
    public bool IsJoined { get; set; }
    public DateTime? JoinTime { get; set; }
}

public sealed class MeetingAuditDto
{
    public string Id { get; set; } = string.Empty;
    public string Action { get; set; } = string.Empty;
    public string ActorId { get; set; } = string.Empty;
    public DateTime OccurredAt { get; set; }
    public int Version { get; set; }
    public string PayloadJson { get; set; } = string.Empty;
}

public sealed class MeetingDetailDto : MeetingListItemDto
{
    public string Agenda { get; set; } = string.Empty;
    public string TimeZone { get; set; } = string.Empty;
    public string CancellationReason { get; set; } = string.Empty;
    public MeetingSettingsDto Settings { get; set; } = new();
    public List<MeetingParticipantDto> Participants { get; set; } = new();
    public List<MeetingAuditDto> Activity { get; set; } = new();
    public string RowVersion { get; set; } = string.Empty;
    public bool CanManage { get; set; }
}

public sealed class MeetingJoinInfoDto
{
    public string MeetingId { get; set; } = string.Empty;
    public string RoomName { get; set; } = string.Empty;
    public string Domain { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public bool IsModerator { get; set; }
    public bool StartWithAudioMuted { get; set; }
    public bool StartWithVideoMuted { get; set; } = true;
}

public sealed class MeetingDashboardDto
{
    public int Upcoming { get; set; }
    public int Ongoing { get; set; }
    public int Ended { get; set; }
    public int Cancelled { get; set; }
    public List<MeetingListItemDto> NextMeetings { get; set; } = new();
}
