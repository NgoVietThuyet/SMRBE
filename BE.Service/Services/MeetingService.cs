using System.Diagnostics;
using System.Security.Cryptography;
using System.Text.Json;
using BE.Core.Data;
using BE.Core.DTOs;
using BE.Core.Entities.AD;
using BE.Core.Entities.MT;
using BE.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace BE.Service.Services;

public sealed class MeetingService : IMeetingService
{
    private readonly AppDbContext _db;
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public MeetingService(AppDbContext db) => _db = db;

    public async Task<MeetingDashboardDto> GetDashboard(string userName, CancellationToken ct = default)
    {
        var query = MemberMeetings(userName).Where(x => !x.Meeting.IsArchived);
        return new MeetingDashboardDto
        {
            Upcoming = await query.CountAsync(x => x.Meeting.Status == (int)MeetingStatus.Scheduled, ct),
            Ongoing = await query.CountAsync(x => x.Meeting.Status == (int)MeetingStatus.Ongoing, ct),
            Ended = await query.CountAsync(x => x.Meeting.Status == (int)MeetingStatus.Ended, ct),
            Cancelled = await query.CountAsync(x => x.Meeting.Status == (int)MeetingStatus.Cancelled, ct),
            NextMeetings = await ProjectList(query.Where(x => x.Meeting.Status == (int)MeetingStatus.Scheduled || x.Meeting.Status == (int)MeetingStatus.Ongoing), userName)
                .OrderBy(x => x.ExpectedStartTime).Take(5).ToListAsync(ct)
        };
    }

    public async Task<PagedResultDto<MeetingListItemDto>> SearchMeetings(MeetingSearchDto dto, string userName, CancellationToken ct = default)
    {
        dto.Page = Math.Max(1, dto.Page);
        dto.PageSize = Math.Clamp(dto.PageSize, 1, 100);
        var query = MemberMeetings(userName);
        query = dto.Tab.ToLowerInvariant() switch
        {
            "upcoming" => query.Where(x => x.Meeting.Status == (int)MeetingStatus.Scheduled),
            "ongoing" => query.Where(x => x.Meeting.Status == (int)MeetingStatus.Ongoing),
            "ended" => query.Where(x => x.Meeting.Status == (int)MeetingStatus.Ended),
            "cancelled" => query.Where(x => x.Meeting.Status == (int)MeetingStatus.Cancelled),
            "draft" => query.Where(x => x.Meeting.Status == (int)MeetingStatus.Draft),
            "archived" => query.Where(x => x.Meeting.IsArchived),
            "me" => query.Where(x => x.Member.IsChuTri),
            _ => query.Where(x => !x.Meeting.IsArchived)
        };
        if (dto.Status.HasValue) query = query.Where(x => x.Meeting.Status == (int)dto.Status.Value);
        if (!string.IsNullOrWhiteSpace(dto.Keyword))
        {
            var keyword = dto.Keyword.Trim();
            query = query.Where(x => x.Meeting.Name.Contains(keyword) || x.Meeting.MeetContent.Contains(keyword) || x.Meeting.RoomCode.Contains(keyword));
        }
        if (!string.IsNullOrWhiteSpace(dto.OrganizationId)) query = query.Where(x => x.Member.OrgId == dto.OrganizationId);
        if (dto.StartDate.HasValue) query = query.Where(x => x.Meeting.ExpectedStartTime >= dto.StartDate.Value.ToUniversalTime());
        if (dto.EndDate.HasValue) query = query.Where(x => x.Meeting.ExpectedStartTime <= dto.EndDate.Value.ToUniversalTime());

        var total = await query.CountAsync(ct);
        var items = await ProjectList(query, userName).OrderByDescending(x => x.ExpectedStartTime).ThenBy(x => x.Id)
            .Skip((dto.Page - 1) * dto.PageSize).Take(dto.PageSize).ToListAsync(ct);
        return new PagedResultDto<MeetingListItemDto>
        {
            Items = items,
            TotalItems = total,
            TotalPages = (int)Math.Ceiling(total / (double)dto.PageSize),
            Page = dto.Page,
            PageSize = dto.PageSize
        };
    }

    public async Task<MeetingDetailDto> GetDetail(string meetingId, string userName, CancellationToken ct = default)
    {
        await EnsureMember(meetingId, userName, ct);
        var meeting = await _db.MeetingInfos.AsNoTracking().FirstOrDefaultAsync(x => x.Id == meetingId && !x.IsDeleted, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy cuộc họp.");
        return await ToDetail(meeting, userName, ct);
    }

    public async Task<MeetingDetailDto> CreateMeeting(CreateMeetingDto dto, string creatorUserName, CancellationToken ct = default)
    {
        ValidateSchedule(dto.Name, dto.ExpectedStartTime, dto.ExpectedEndTime, dto.SaveAsDraft);
        var requested = MergeParticipants(dto.Participants, dto.ParticipantUserNames, creatorUserName);
        var accounts = await GetAccounts(requested.Select(x => x.UserName), ct);
        if (!dto.SaveAsDraft) await EnsureNoConflict(creatorUserName, dto.ExpectedStartTime, dto.ExpectedEndTime, null, ct);

        var now = DateTime.UtcNow;
        var meeting = new MeetingInfo
        {
            Id = Guid.NewGuid().ToString("N"),
            Name = dto.Name.Trim(),
            MeetContent = dto.Description.Trim(),
            Agenda = dto.Agenda.Trim(),
            ExpectedStartTime = dto.ExpectedStartTime.ToUniversalTime(),
            ExpectedEndTime = dto.ExpectedEndTime?.ToUniversalTime(),
            TimeZone = dto.TimeZone.Trim(),
            Status = dto.SaveAsDraft ? (int)MeetingStatus.Draft : (int)MeetingStatus.Scheduled,
            Visibility = (int)dto.Visibility,
            IsDraft = dto.SaveAsDraft,
            RoomCode = await NewRoomCode(ct),
            SettingsJson = JsonSerializer.Serialize(dto.Settings, JsonOptions),
            RefrenceFileId = Guid.NewGuid().ToString("N"),
            Notes = string.Empty,
            CancellationReason = string.Empty,
            CreateBy = creatorUserName,
            CreateDate = now,
            UpdateBy = creatorUserName,
            UpdateDate = now,
            Version = 1
        };
        meeting.JoinUrl = $"/meet/{meeting.Id}";
        await using var tx = await _db.Database.BeginTransactionAsync(ct);
        _db.MeetingInfos.Add(meeting);
        foreach (var item in requested)
        {
            var account = accounts.Single(x => x.UserName.Equals(item.UserName, StringComparison.OrdinalIgnoreCase));
            _db.MeetingPersonals.Add(ToParticipant(meeting, account, item.Role, now));
        }
        AddAudit(meeting, "meeting.created", creatorUserName, new { dto.PublishInvitation });
        await _db.SaveChangesAsync(ct);
        await tx.CommitAsync(ct);
        return await ToDetail(meeting, creatorUserName, ct);
    }

    public Task<MeetingDetailDto> CreateQuickMeeting(QuickMeetingDto dto, string creatorUserName, CancellationToken ct = default)
    {
        var now = DateTime.UtcNow;
        return CreateMeeting(new CreateMeetingDto
        {
            Name = string.IsNullOrWhiteSpace(dto.Name) ? "Cuộc họp nhanh" : dto.Name,
            Description = "Cuộc họp nhanh",
            ExpectedStartTime = now,
            ExpectedEndTime = now.AddHours(1),
            ParticipantUserNames = dto.ParticipantUserNames
        }, creatorUserName, ct).ContinueWith(async task =>
        {
            var detail = await task;
            await StartMeeting(detail.Id, creatorUserName, ct);
            return await GetDetail(detail.Id, creatorUserName, ct);
        }, ct, TaskContinuationOptions.ExecuteSynchronously, TaskScheduler.Default).Unwrap();
    }

    public async Task<MeetingDetailDto> UpdateMeeting(UpdateMeetingDto dto, string userName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(dto.Id, userName, ct);
        if (meeting.Status is (int)MeetingStatus.Ended or (int)MeetingStatus.Cancelled or (int)MeetingStatus.Archived)
            throw new InvalidOperationException("Trạng thái hiện tại không cho phép chỉnh sửa.");
        ValidateSchedule(dto.Name, dto.ExpectedStartTime, dto.ExpectedEndTime, meeting.IsDraft);
        await EnsureNoConflict(userName, dto.ExpectedStartTime, dto.ExpectedEndTime, meeting.Id, ct);
        if (!string.IsNullOrWhiteSpace(dto.RowVersion)) _db.Entry(meeting).Property(x => x.RowVersion).OriginalValue = Convert.FromBase64String(dto.RowVersion);
        meeting.Name = dto.Name.Trim(); meeting.MeetContent = dto.Description.Trim(); meeting.Agenda = dto.Agenda.Trim();
        meeting.ExpectedStartTime = dto.ExpectedStartTime.ToUniversalTime(); meeting.ExpectedEndTime = dto.ExpectedEndTime?.ToUniversalTime();
        meeting.TimeZone = dto.TimeZone.Trim(); meeting.Visibility = (int)dto.Visibility; meeting.SettingsJson = JsonSerializer.Serialize(dto.Settings, JsonOptions);
        Touch(meeting, userName); AddAudit(meeting, "meeting.updated", userName, null);
        try { await _db.SaveChangesAsync(ct); }
        catch (DbUpdateConcurrencyException) { throw new InvalidOperationException("Cuộc họp vừa được người khác cập nhật. Vui lòng tải lại dữ liệu."); }
        return await ToDetail(meeting, userName, ct);
    }

    public async Task CancelMeeting(CancelMeetingDto dto, string userName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(dto.MeetingId, userName, ct);
        if (meeting.Status is not ((int)MeetingStatus.Draft) and not ((int)MeetingStatus.Scheduled)) throw new InvalidOperationException("Chỉ có thể hủy cuộc họp chưa bắt đầu.");
        meeting.Status = (int)MeetingStatus.Cancelled; meeting.IsDraft = false; meeting.CancellationReason = dto.Reason.Trim();
        Touch(meeting, userName); AddAudit(meeting, "meeting.cancelled", userName, new { reason = meeting.CancellationReason });
        await _db.SaveChangesAsync(ct);
    }

    public async Task ArchiveMeeting(string meetingId, string userName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(meetingId, userName, ct);
        if (meeting.Status != (int)MeetingStatus.Ended) throw new InvalidOperationException("Chỉ cuộc họp đã kết thúc mới có thể lưu trữ.");
        meeting.Status = (int)MeetingStatus.Archived; meeting.IsArchived = true; Touch(meeting, userName); AddAudit(meeting, "meeting.archived", userName, null);
        await _db.SaveChangesAsync(ct);
    }

    public async Task DeleteDraft(string meetingId, string userName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(meetingId, userName, ct);
        if (meeting.Status != (int)MeetingStatus.Draft) throw new InvalidOperationException("Chỉ bản nháp mới có thể xóa.");
        meeting.IsDeleted = true; Touch(meeting, userName); AddAudit(meeting, "meeting.deleted", userName, null); await _db.SaveChangesAsync(ct);
    }

    public async Task AddParticipants(UpdateMeetingParticipantsDto dto, string actorUserName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(dto.MeetingId, actorUserName, ct); EnsureEditable(meeting);
        var requested = dto.Participants.Concat(dto.UserNames.Select(x => new MeetingParticipantInputDto { UserName = x })).Where(x => !string.IsNullOrWhiteSpace(x.UserName)).GroupBy(x => x.UserName, StringComparer.OrdinalIgnoreCase).Select(x => x.First()).ToList();
        var existing = await _db.MeetingPersonals.Where(x => x.MeetingId == dto.MeetingId).Select(x => x.UserName).ToListAsync(ct);
        requested = requested.Where(x => !existing.Contains(x.UserName, StringComparer.OrdinalIgnoreCase)).ToList();
        var accounts = await GetAccounts(requested.Select(x => x.UserName), ct); var now = DateTime.UtcNow;
        foreach (var item in requested) _db.MeetingPersonals.Add(ToParticipant(meeting, accounts.Single(x => x.UserName.Equals(item.UserName, StringComparison.OrdinalIgnoreCase)), item.Role, now));
        Touch(meeting, actorUserName); AddAudit(meeting, "meeting.participants.added", actorUserName, new { users = requested.Select(x => x.UserName) }); await _db.SaveChangesAsync(ct);
    }

    public async Task RemoveParticipant(RemoveMeetingParticipantDto dto, string actorUserName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(dto.MeetingId, actorUserName, ct); EnsureEditable(meeting);
        var participant = await _db.MeetingPersonals.FirstOrDefaultAsync(x => x.MeetingId == dto.MeetingId && x.UserName == dto.UserName, ct) ?? throw new KeyNotFoundException("Không tìm thấy người tham gia.");
        if (participant.IsChuTri) throw new InvalidOperationException("Không thể xóa chủ trì.");
        _db.MeetingPersonals.Remove(participant); Touch(meeting, actorUserName); AddAudit(meeting, "meeting.participant.removed", actorUserName, new { dto.UserName }); await _db.SaveChangesAsync(ct);
    }

    public async Task StartMeeting(string meetingId, string userName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(meetingId, userName, ct);
        if (meeting.Status != (int)MeetingStatus.Scheduled) throw new InvalidOperationException("Chỉ cuộc họp đã lên lịch mới có thể bắt đầu.");
        meeting.Status = (int)MeetingStatus.Ongoing; meeting.StartDate = DateTime.UtcNow; Touch(meeting, userName); AddAudit(meeting, "meeting.started", userName, null); await _db.SaveChangesAsync(ct);
    }

    public async Task EndMeeting(string meetingId, string userName, CancellationToken ct = default)
    {
        var meeting = await EnsureManager(meetingId, userName, ct);
        if (meeting.Status != (int)MeetingStatus.Ongoing) throw new InvalidOperationException("Cuộc họp chưa diễn ra.");
        meeting.Status = (int)MeetingStatus.Ended; meeting.EndDate = DateTime.UtcNow; Touch(meeting, userName); AddAudit(meeting, "meeting.ended", userName, null);
        var joined = await _db.MeetingPersonals.Where(x => x.MeetingId == meetingId && x.IsJoined).ToListAsync(ct); joined.ForEach(x => x.IsJoined = false); await _db.SaveChangesAsync(ct);
    }

    public async Task IntoTheMeeting(string meetingId, string userName, CancellationToken ct = default)
    {
        var meeting = await _db.MeetingInfos.FirstOrDefaultAsync(x => x.Id == meetingId && !x.IsDeleted, ct) ?? throw new KeyNotFoundException("Không tìm thấy cuộc họp.");
        if (meeting.Status != (int)MeetingStatus.Ongoing) throw new InvalidOperationException("Cuộc họp chưa bắt đầu hoặc đã kết thúc.");
        var member = await EnsureMember(meetingId, userName, ct); member.IsJoined = true; member.JoinTime ??= DateTime.UtcNow; await _db.SaveChangesAsync(ct);
    }

    public async Task ExitTheMeeting(string meetingId, string userName, CancellationToken ct = default)
    { var member = await EnsureMember(meetingId, userName, ct); member.IsJoined = false; await _db.SaveChangesAsync(ct); }

    public async Task<List<object>> GetMessages(string meetingId, string userName, CancellationToken ct = default)
    {
        await EnsureMember(meetingId, userName, ct);
        return await (from message in _db.MeetingMessages.AsNoTracking() join account in _db.AdAccounts.AsNoTracking() on message.SenderUserId equals account.UserName into users from account in users.DefaultIfEmpty() where message.MeetingId == meetingId orderby message.CreateDate select (object)new { message.Id, message.SenderUserId, SenderName = account == null ? message.SenderUserId : account.FullName, message.MessageText, CreatedAt = message.CreateDate }).ToListAsync(ct);
    }

    public async Task<object> SendMessage(string meetingId, string userName, string messageText, CancellationToken ct = default)
    {
        await EnsureMember(meetingId, userName, ct); var text = messageText.Trim(); if (text.Length is < 1 or > 2000) throw new ArgumentException("Tin nhắn phải có từ 1 đến 2000 ký tự.");
        var entity = new MeetingMessage { Id = Guid.NewGuid().ToString("N"), MeetingId = meetingId, SenderUserId = userName, ReceiverUserId = string.Empty, MessageText = text, CreateBy = userName, CreateDate = DateTime.UtcNow, UpdateBy = userName, UpdateDate = DateTime.UtcNow };
        _db.MeetingMessages.Add(entity); await _db.SaveChangesAsync(ct); var name = await _db.AdAccounts.Where(x => x.UserName == userName).Select(x => x.FullName).FirstAsync(ct); return new { entity.Id, entity.SenderUserId, SenderName = name, entity.MessageText, CreatedAt = entity.CreateDate };
    }

    public async Task<List<MeetingInfo>> GetMeetings(string userName, CancellationToken ct = default) => await MemberMeetings(userName).OrderByDescending(x => x.Meeting.ExpectedStartTime).Select(x => x.Meeting).AsNoTracking().ToListAsync(ct);
    public async Task<MeetingInfo?> GetInfoMeeting(string meetingId, string userName, CancellationToken ct = default) { await EnsureMember(meetingId, userName, ct); return await _db.MeetingInfos.AsNoTracking().FirstOrDefaultAsync(x => x.Id == meetingId && !x.IsDeleted, ct); }
    public async Task<List<MeetingPersonal>> GetPersonalMeeting(string meetingId, string userName, CancellationToken ct = default) { await EnsureMember(meetingId, userName, ct); return await _db.MeetingPersonals.AsNoTracking().Where(x => x.MeetingId == meetingId).OrderByDescending(x => x.IsChuTri).ThenBy(x => x.FullName).ToListAsync(ct); }

    private record MeetingScope(MeetingInfo Meeting, MeetingPersonal Member);
    private IQueryable<MeetingScope> MemberMeetings(string userName) => from meeting in _db.MeetingInfos.AsNoTracking() join member in _db.MeetingPersonals.AsNoTracking() on meeting.Id equals member.MeetingId where member.UserName == userName && !meeting.IsDeleted select new MeetingScope(meeting, member);
    private IQueryable<MeetingListItemDto> ProjectList(IQueryable<MeetingScope> query, string userName) => query.Select(x => new MeetingListItemDto { Id = x.Meeting.Id, Name = x.Meeting.Name, Description = x.Meeting.MeetContent, ExpectedStartTime = x.Meeting.ExpectedStartTime, ExpectedEndTime = x.Meeting.ExpectedEndTime, Status = (MeetingStatus)x.Meeting.Status, Visibility = (MeetingVisibility)x.Meeting.Visibility, RoomCode = x.Meeting.RoomCode, JoinUrl = x.Meeting.JoinUrl, IsHost = x.Member.IsChuTri, ParticipantCount = _db.MeetingPersonals.Count(p => p.MeetingId == x.Meeting.Id), HostName = _db.MeetingPersonals.Where(p => p.MeetingId == x.Meeting.Id && p.IsChuTri).Select(p => p.FullName).FirstOrDefault() ?? string.Empty });

    private async Task<MeetingDetailDto> ToDetail(MeetingInfo meeting, string userName, CancellationToken ct)
    {
        var participants = await _db.MeetingPersonals.AsNoTracking().Where(x => x.MeetingId == meeting.Id).OrderByDescending(x => x.IsChuTri).ThenBy(x => x.FullName).Select(x => new MeetingParticipantDto { UserName = x.UserName, FullName = x.FullName, Email = x.Email, OrganizationId = x.OrgId, TitleCode = x.TitleCode, Role = (MeetingParticipantRole)x.Type, IsJoined = x.IsJoined, JoinTime = x.JoinTime }).ToListAsync(ct);
        var activity = await _db.MeetingAuditLogs.AsNoTracking().Where(x => x.MeetingId == meeting.Id).OrderByDescending(x => x.OccurredAt).Select(x => new MeetingAuditDto { Id = x.Id, Action = x.Action, ActorId = x.ActorId, OccurredAt = x.OccurredAt, Version = x.Version, PayloadJson = x.PayloadJson }).ToListAsync(ct);
        var settings = JsonSerializer.Deserialize<MeetingSettingsDto>(meeting.SettingsJson, JsonOptions) ?? new(); settings.PasswordHash = string.Empty;
        var host = participants.FirstOrDefault(x => x.Role == MeetingParticipantRole.Host);
        return new MeetingDetailDto { Id = meeting.Id, Name = meeting.Name, Description = meeting.MeetContent, Agenda = meeting.Agenda, ExpectedStartTime = meeting.ExpectedStartTime, ExpectedEndTime = meeting.ExpectedEndTime, TimeZone = meeting.TimeZone, Status = (MeetingStatus)meeting.Status, Visibility = (MeetingVisibility)meeting.Visibility, RoomCode = meeting.RoomCode, JoinUrl = meeting.JoinUrl, ParticipantCount = participants.Count, HostName = host?.FullName ?? string.Empty, IsHost = host?.UserName.Equals(userName, StringComparison.OrdinalIgnoreCase) == true, CanManage = participants.Any(x => x.UserName.Equals(userName, StringComparison.OrdinalIgnoreCase) && x.Role is MeetingParticipantRole.Host or MeetingParticipantRole.CoHost), CancellationReason = meeting.CancellationReason, Settings = settings, Participants = participants, Activity = activity, RowVersion = meeting.RowVersion.Length == 0 ? string.Empty : Convert.ToBase64String(meeting.RowVersion) };
    }

    private async Task<MeetingPersonal> EnsureMember(string id, string user, CancellationToken ct) => await _db.MeetingPersonals.FirstOrDefaultAsync(x => x.MeetingId == id && x.UserName == user, ct) ?? throw new UnauthorizedAccessException("Bạn không thuộc cuộc họp này.");
    private async Task<MeetingInfo> EnsureManager(string id, string user, CancellationToken ct) { var member = await EnsureMember(id, user, ct); if (!member.IsChuTri && member.Type != (int)MeetingParticipantRole.CoHost) throw new UnauthorizedAccessException("Bạn không có quyền quản lý cuộc họp."); return await _db.MeetingInfos.FirstOrDefaultAsync(x => x.Id == id && !x.IsDeleted, ct) ?? throw new KeyNotFoundException("Không tìm thấy cuộc họp."); }
    private async Task EnsureNoConflict(string user, DateTime start, DateTime? end, string? exclude, CancellationToken ct) { var s = start.ToUniversalTime(); var e = (end ?? start.AddHours(1)).ToUniversalTime(); var conflict = await (from m in _db.MeetingInfos join p in _db.MeetingPersonals on m.Id equals p.MeetingId where p.UserName == user && p.IsChuTri && !m.IsDeleted && m.Status != (int)MeetingStatus.Cancelled && m.Status != (int)MeetingStatus.Ended && (exclude == null || m.Id != exclude) && m.ExpectedStartTime < e && (m.ExpectedEndTime ?? m.ExpectedStartTime.AddHours(1)) > s select m).AnyAsync(ct); if (conflict) throw new InvalidOperationException("Thời gian này trùng với một cuộc họp khác do bạn chủ trì."); }
    private async Task<List<AdAccount>> GetAccounts(IEnumerable<string> names, CancellationToken ct) { var requested = names.Where(x => !string.IsNullOrWhiteSpace(x)).Select(x => x.Trim()).Distinct(StringComparer.OrdinalIgnoreCase).ToList(); var accounts = await _db.AdAccounts.Where(x => requested.Contains(x.UserName) && x.IsActive).ToListAsync(ct); var missing = requested.Except(accounts.Select(x => x.UserName), StringComparer.OrdinalIgnoreCase).ToList(); if (missing.Count > 0) throw new ArgumentException($"Không tìm thấy tài khoản hoạt động: {string.Join(", ", missing)}."); return accounts; }
    private static List<MeetingParticipantInputDto> MergeParticipants(IEnumerable<MeetingParticipantInputDto> inputs, IEnumerable<string> legacy, string creator) { var all = inputs.Concat(legacy.Select(x => new MeetingParticipantInputDto { UserName = x })).Where(x => !string.IsNullOrWhiteSpace(x.UserName)).ToList(); all.RemoveAll(x => x.UserName.Equals(creator, StringComparison.OrdinalIgnoreCase)); all.Insert(0, new MeetingParticipantInputDto { UserName = creator, Role = MeetingParticipantRole.Host }); return all.GroupBy(x => x.UserName, StringComparer.OrdinalIgnoreCase).Select(x => x.First()).ToList(); }
    private static MeetingPersonal ToParticipant(MeetingInfo m, AdAccount a, MeetingParticipantRole role, DateTime now) => new() { Id = Guid.NewGuid().ToString("N"), MeetingId = m.Id, UserName = a.UserName, FullName = a.FullName, Phone = a.Phone ?? string.Empty, Email = a.Email ?? string.Empty, Address = a.Address ?? string.Empty, OrgId = a.OrgId ?? string.Empty, TitleCode = a.TitleCode ?? string.Empty, RefrenceFileId = m.RefrenceFileId, Type = (int)role, IsChuTri = role == MeetingParticipantRole.Host, CreateBy = m.CreateBy, CreateDate = now, UpdateBy = m.CreateBy, UpdateDate = now };
    private static void ValidateSchedule(string name, DateTime start, DateTime? end, bool draft) { if (string.IsNullOrWhiteSpace(name)) throw new ArgumentException("Tên cuộc họp không được để trống."); if (start == default) throw new ArgumentException("Thời gian bắt đầu không hợp lệ."); if (end.HasValue && end <= start) throw new ArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu."); if (end.HasValue && end.Value - start > TimeSpan.FromHours(24)) throw new ArgumentException("Cuộc họp không được dài quá 24 giờ."); if (!draft && start.ToUniversalTime() < DateTime.UtcNow.AddMinutes(-5)) throw new ArgumentException("Không thể lên lịch cuộc họp trong quá khứ."); }
    private static void EnsureEditable(MeetingInfo meeting) { if (meeting.Status is (int)MeetingStatus.Ended or (int)MeetingStatus.Cancelled or (int)MeetingStatus.Archived) throw new InvalidOperationException("Trạng thái hiện tại không cho phép thay đổi người tham gia."); }
    private static void Touch(MeetingInfo meeting, string actor) { meeting.UpdateBy = actor; meeting.UpdateDate = DateTime.UtcNow; meeting.Version++; }
    private void AddAudit(MeetingInfo meeting, string action, string actor, object? payload) => _db.MeetingAuditLogs.Add(new MeetingAuditLog { Id = Guid.NewGuid().ToString("N"), MeetingId = meeting.Id, Action = action, ActorId = actor, OccurredAt = DateTime.UtcNow, CorrelationId = Activity.Current?.Id ?? Guid.NewGuid().ToString("N"), Version = meeting.Version, PayloadJson = payload == null ? "{}" : JsonSerializer.Serialize(payload, JsonOptions) });
    private async Task<string> NewRoomCode(CancellationToken ct) { const string chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; for (var attempt = 0; attempt < 5; attempt++) { var bytes = RandomNumberGenerator.GetBytes(10); var code = new string(bytes.Select(x => chars[x % chars.Length]).ToArray()); if (!await _db.MeetingInfos.AnyAsync(x => x.RoomCode == code, ct)) return code; } throw new InvalidOperationException("Không thể cấp mã phòng họp."); }

    private sealed record MeetingJoin(MeetingInfo Meeting, MeetingPersonal Member);
}
