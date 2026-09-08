using BE.Core.DTOs;
using BE.Core.Entities.MT;

namespace BE.Core.Data;

public interface IMeetingService
{
    Task<MeetingDashboardDto> GetDashboard(string userName, CancellationToken ct = default);
    Task<PagedResultDto<MeetingListItemDto>> SearchMeetings(MeetingSearchDto dto, string userName, CancellationToken ct = default);
    Task<MeetingDetailDto> GetDetail(string meetingId, string userName, CancellationToken ct = default);
    Task<MeetingDetailDto> CreateMeeting(CreateMeetingDto dto, string creatorUserName, CancellationToken ct = default);
    Task<MeetingDetailDto> CreateQuickMeeting(QuickMeetingDto dto, string creatorUserName, CancellationToken ct = default);
    Task<MeetingDetailDto> UpdateMeeting(UpdateMeetingDto dto, string userName, CancellationToken ct = default);
    Task CancelMeeting(CancelMeetingDto dto, string userName, CancellationToken ct = default);
    Task ArchiveMeeting(string meetingId, string userName, CancellationToken ct = default);
    Task DeleteDraft(string meetingId, string userName, CancellationToken ct = default);
    Task AddParticipants(UpdateMeetingParticipantsDto dto, string actorUserName, CancellationToken ct = default);
    Task RemoveParticipant(RemoveMeetingParticipantDto dto, string actorUserName, CancellationToken ct = default);
    Task StartMeeting(string meetingId, string userName, CancellationToken ct = default);
    Task EndMeeting(string meetingId, string userName, CancellationToken ct = default);
    Task IntoTheMeeting(string meetingId, string userName, CancellationToken ct = default);
    Task ExitTheMeeting(string meetingId, string userName, CancellationToken ct = default);
    Task<List<object>> GetMessages(string meetingId, string userName, CancellationToken ct = default);
    Task<object> SendMessage(string meetingId, string userName, string messageText, CancellationToken ct = default);

    // Compatibility for screens still using the original contract.
    Task<List<MeetingInfo>> GetMeetings(string userName, CancellationToken ct = default);
    Task<MeetingInfo?> GetInfoMeeting(string meetingId, string userName, CancellationToken ct = default);
    Task<List<MeetingPersonal>> GetPersonalMeeting(string meetingId, string userName, CancellationToken ct = default);
}
