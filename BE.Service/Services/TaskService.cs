using BE.Core.Data;
using BE.Core.DTOs;
using BE.Core.Entities.MT;
using BE.Infrastructure;
using Microsoft.EntityFrameworkCore;
// Tránh xung đột tên với System.Threading.Tasks.TaskStatus (implicit usings).
using TaskStatus = BE.Core.DTOs.TaskStatus;

namespace BE.Service.Services;

// Công việc: xem = người tạo, task công khai, người phụ trách, người được share, thành viên cuộc họp gắn kèm + kế thừa quyền từ parent.
// Sửa nội dung = người tạo hoặc người được share quyền Sửa; quản trị share/public + xóa = chỉ người tạo.
public sealed class TaskService : ITaskService
{
    private readonly AppDbContext _db;

    public TaskService(AppDbContext db) { _db = db; }

    public async Task<TaskSearchResultDto> SearchTasks(TaskSearchDto dto, string userName, CancellationToken ct = default)
    {
        dto.Page = Math.Max(1, dto.Page);
        dto.PageSize = Math.Clamp(dto.PageSize, 1, 100);
        var query = ViewableTasks(userName);

        if (!string.IsNullOrWhiteSpace(dto.Keyword))
        {
            var keyword = dto.Keyword.Trim();
            query = query.Where(t => t.Title.Contains(keyword) || (t.Description != null && t.Description.Contains(keyword)));
        }
        if (!string.IsNullOrWhiteSpace(dto.MeetingId))
            query = dto.MeetingId == "none"
                ? query.Where(t => t.MeetingId == null)
                : query.Where(t => t.MeetingId == dto.MeetingId);
        if (!string.IsNullOrWhiteSpace(dto.Assignee))
            query = dto.Assignee == "none"
                ? query.Where(t => t.AssigneeUserName == null)
                : query.Where(t => t.AssigneeUserName == dto.Assignee);
        if (dto.Status.HasValue) query = query.Where(t => t.Status == (int)dto.Status.Value);
        if (dto.Priority.HasValue) query = query.Where(t => t.Priority == (int)dto.Priority.Value);
        if (dto.Level.HasValue) query = query.Where(t => t.Level == dto.Level.Value);
        if (!string.IsNullOrWhiteSpace(dto.ParentId))
            query = query.Where(t => t.ParentId == dto.ParentId);

        var todayStart = DateTime.Today.ToUniversalTime();
        var tomorrowStart = DateTime.Today.AddDays(1).ToUniversalTime();
        var summary = new TaskSummaryDto
        {
            Total = await query.CountAsync(ct),
            Mine = await query.CountAsync(t => t.CreateBy == userName || t.AssigneeUserName == userName
                || _db.MeetingTaskShares.Any(s => s.TaskId == t.Id && s.UserName == userName), ct),
            Overdue = await query.CountAsync(t => t.DueDate != null && t.DueDate < todayStart && t.Status != (int)TaskStatus.Completed, ct),
            Today = await query.CountAsync(t => t.DueDate != null && t.DueDate >= todayStart && t.DueDate < tomorrowStart && t.Status != (int)TaskStatus.Completed, ct)
        };

        var filtered = dto.Shortcut?.ToLowerInvariant() switch
        {
            "mine" => query.Where(t => t.CreateBy == userName || t.AssigneeUserName == userName
                || _db.MeetingTaskShares.Any(s => s.TaskId == t.Id && s.UserName == userName)),
            "overdue" => query.Where(t => t.DueDate != null && t.DueDate < todayStart && t.Status != (int)TaskStatus.Completed),
            "today" => query.Where(t => t.DueDate != null && t.DueDate >= todayStart && t.DueDate < tomorrowStart && t.Status != (int)TaskStatus.Completed),
            _ => query
        };

        var totalItems = await filtered.CountAsync(ct);
        var items = await ProjectList(filtered, userName)
            .OrderByDescending(t => t.MeetingStartTime ?? DateTime.MinValue)
            .ThenBy(t => t.DueDate ?? DateTime.MaxValue)
            .ThenByDescending(t => t.CreateDate)
            .Skip((dto.Page - 1) * dto.PageSize).Take(dto.PageSize).ToListAsync(ct);
        return new TaskSearchResultDto
        {
            Items = items,
            TotalItems = totalItems,
            TotalPages = (int)Math.Ceiling(totalItems / (double)dto.PageSize),
            Page = dto.Page,
            PageSize = dto.PageSize,
            Summary = summary
        };
    }

    public async Task<TaskDetailDto> GetTask(string taskId, string userName, CancellationToken ct = default)
    {
        var task = await ViewableTasks(userName).AsNoTracking().FirstOrDefaultAsync(t => t.Id == taskId, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy công việc.");
        return await ToDetail(task, userName, ct);
    }

    public async Task<TaskListItemDto> CreateTask(CreateTaskDto dto, string creatorUserName, CancellationToken ct = default)
    {
        var title = (dto.Title ?? string.Empty).Trim();
        if (title.Length is < 1 or > 300) throw new ArgumentException("Tên công việc phải có từ 1 đến 300 ký tự.");
        ValidateEnums(dto.Status, dto.Priority);

        MeetingTask? parent = null;
        int level = dto.Level;
        string? parentId = string.IsNullOrWhiteSpace(dto.ParentId) ? null : dto.ParentId.Trim();
        if (parentId != null)
        {
            parent = await _db.MeetingTasks.FirstOrDefaultAsync(x => x.Id == parentId, ct)
                ?? throw new KeyNotFoundException("Không tìm thấy công việc cha.");
            // Kiểm tra quyền xem parent
            var canViewParent = await ViewableTasks(creatorUserName).AnyAsync(x => x.Id == parentId, ct);
            if (!canViewParent) throw new UnauthorizedAccessException("Bạn không có quyền tạo công việc con trong công việc này.");
            if (parent.Level >= 2) throw new InvalidOperationException("Công việc đã đạt độ sâu tối đa (3 cấp).");
            level = parent.Level + 1;
            if (parent.Level == 0 && dto.Level == 0 && parentId != null) level = 1; // force
        }
        else
        {
            level = 0;
        }
        if (level < 0 || level > 2) throw new ArgumentException("Cấp công việc không hợp lệ (0-2).");

        string? meetingId = null;
        if (!string.IsNullOrWhiteSpace(dto.MeetingId))
        {
            meetingId = dto.MeetingId.Trim();
            _ = await _db.MeetingInfos.AsNoTracking().FirstOrDefaultAsync(x => x.Id == meetingId && !x.IsDeleted, ct)
                ?? throw new KeyNotFoundException("Không tìm thấy cuộc họp.");
            if (!await _db.MeetingPersonals.AsNoTracking().AnyAsync(p => p.MeetingId == meetingId && p.UserName == creatorUserName, ct))
                throw new UnauthorizedAccessException("Bạn không thuộc cuộc họp này nên không thể tạo công việc cho cuộc họp.");
        }
        else if (parent?.MeetingId != null)
        {
            // Kế thừa meeting từ parent nếu không chỉ định
            meetingId = parent.MeetingId;
        }
        var assignee = await ResolveAssignee(dto.AssigneeUserName, ct);

        var now = DateTime.UtcNow;
        var task = new MeetingTask
        {
            Id = Guid.NewGuid().ToString("N"),
            MeetingId = meetingId,
            ParentId = parentId,
            Level = level,
            Title = title,
            Description = string.IsNullOrWhiteSpace(dto.Description) ? null : dto.Description.Trim(),
            AssigneeUserName = assignee,
            DueDate = dto.DueDate?.ToUniversalTime(),
            Status = (int)dto.Status,
            Priority = (int)dto.Priority,
            IsPublic = dto.IsPublic,
            SourceRef = string.IsNullOrWhiteSpace(dto.SourceRef) ? null : dto.SourceRef.Trim(),
            CreateBy = creatorUserName,
            CreateDate = now,
            UpdateBy = creatorUserName,
            UpdateDate = now
        };
        _db.MeetingTasks.Add(task);
        await _db.SaveChangesAsync(ct);
        return await ProjectList(_db.MeetingTasks.AsNoTracking().Where(t => t.Id == task.Id), creatorUserName).FirstAsync(ct);
    }

    public async Task<TaskListItemDto> UpdateTask(string taskId, UpdateTaskDto dto, string userName, CancellationToken ct = default)
    {
        var title = (dto.Title ?? string.Empty).Trim();
        if (title.Length is < 1 or > 300) throw new ArgumentException("Tên công việc phải có từ 1 đến 300 ký tự.");
        ValidateEnums(dto.Status, dto.Priority);

        var task = await ViewableTasks(userName).FirstOrDefaultAsync(t => t.Id == taskId, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy công việc.");
        if (task.CreateBy != userName
            && !await _db.MeetingTaskShares.AnyAsync(s => s.TaskId == taskId && s.UserName == userName && s.Permission == (int)TaskSharePermission.Edit, ct))
            throw new UnauthorizedAccessException("Bạn chỉ có quyền xem công việc này.");

        // Hierarchy move if ParentId provided
        if (dto.ParentId != null)
        {
            var newParentId = string.IsNullOrWhiteSpace(dto.ParentId) ? null : dto.ParentId.Trim();
            if (newParentId != task.ParentId)
            {
                if (newParentId == task.Id) throw new InvalidOperationException("Không thể đặt công việc làm cha của chính nó.");
                if (newParentId != null)
                {
                    var parent = await _db.MeetingTasks.FirstOrDefaultAsync(x => x.Id == newParentId, ct)
                        ?? throw new KeyNotFoundException("Không tìm thấy công việc cha.");
                    if (parent.Level >= 2) throw new InvalidOperationException("Công việc cha đã ở cấp sâu nhất.");
                    // Cycle check: không cho parent là descendant của task
                    var isDescendant = await IsDescendant(newParentId, taskId, ct);
                    if (isDescendant) throw new InvalidOperationException("Không thể di chuyển tạo vòng lặp.");
                    task.ParentId = newParentId;
                    task.Level = parent.Level + 1;
                    // Validate descendants depth
                    var maxDescDepth = await GetMaxDescendantDepth(taskId, ct);
                    if (task.Level + maxDescDepth > 2) throw new InvalidOperationException("Di chuyển sẽ vượt quá độ sâu 3 cấp.");
                }
                else
                {
                    // Move to root
                    var maxDescDepth = await GetMaxDescendantDepth(taskId, ct);
                    if (maxDescDepth > 2) throw new InvalidOperationException("Cây con quá sâu khi đưa về gốc.");
                    task.ParentId = null;
                    task.Level = 0;
                }
            }
        }
        if (dto.Level.HasValue && dto.ParentId == null && dto.Level.Value != task.Level)
        {
            // Cho phép đổi level khi không đổi parent (hiếm) — chỉ khi không có con
            var hasChildren = await _db.MeetingTasks.AnyAsync(x => x.ParentId == taskId, ct);
            if (hasChildren) throw new InvalidOperationException("Không thể đổi cấp khi còn công việc con.");
            if (dto.Level.Value < 0 || dto.Level.Value > 2) throw new ArgumentException("Cấp không hợp lệ.");
            task.Level = dto.Level.Value;
        }

        task.Title = title;
        task.Description = string.IsNullOrWhiteSpace(dto.Description) ? null : dto.Description.Trim();
        task.AssigneeUserName = await ResolveAssignee(dto.AssigneeUserName, ct);
        task.DueDate = dto.DueDate?.ToUniversalTime();
        task.Status = (int)dto.Status;
        task.Priority = (int)dto.Priority;
        if (!string.IsNullOrWhiteSpace(dto.SourceRef)) {
            task.SourceRef = dto.SourceRef.Trim();
        }
        Touch(task, userName);
        await _db.SaveChangesAsync(ct);
        // Nếu task có con, cập nhật Level cho toàn cây con (đệ quy 2 cấp)
        await PropagateLevel(task.Id, task.Level, ct);
        return await ProjectList(_db.MeetingTasks.AsNoTracking().Where(t => t.Id == taskId), userName).FirstAsync(ct);
    }

    public async Task<TaskListItemDto> DeleteTask(string taskId, string userName, CancellationToken ct = default)
    {
        var task = await _db.MeetingTasks.FirstOrDefaultAsync(t => t.Id == taskId, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy công việc.");
        if (task.CreateBy != userName) throw new UnauthorizedAccessException("Chỉ người tạo công việc mới được xóa.");
        var hasChildren = await _db.MeetingTasks.AnyAsync(x => x.ParentId == taskId, ct);
        if (hasChildren) throw new InvalidOperationException("Không thể xóa công việc còn chứa công việc con. Hãy xóa con trước.");
        var snapshot = await ProjectList(_db.MeetingTasks.AsNoTracking().Where(t => t.Id == taskId), userName).FirstAsync(ct);
        _db.MeetingTasks.Remove(task); // Shares cascade
        await _db.SaveChangesAsync(ct);
        return snapshot;
    }

    public async Task<TaskListItemDto> UpdateTaskStatus(string taskId, UpdateTaskStatusDto dto, string userName, CancellationToken ct = default)
    {
        if (!Enum.IsDefined(dto.Status)) throw new ArgumentException("Trạng thái công việc không hợp lệ.");
        
        var task = await ViewableTasks(userName).FirstOrDefaultAsync(t => t.Id == taskId, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy công việc.");
        if (task.CreateBy != userName
            && !await _db.MeetingTaskShares.AnyAsync(s => s.TaskId == taskId && s.UserName == userName && s.Permission == (int)TaskSharePermission.Edit, ct))
            throw new UnauthorizedAccessException("Bạn chỉ có quyền xem công việc này.");

        task.Status = (int)dto.Status;
        Touch(task, userName);
        await _db.SaveChangesAsync(ct);
        return await ProjectList(_db.MeetingTasks.AsNoTracking().Where(t => t.Id == taskId), userName).FirstAsync(ct);
    }

    public async Task SetVisibility(string taskId, TaskVisibilityDto dto, string userName, CancellationToken ct = default)
    {
        var task = await EnsureCreator(taskId, userName, ct);
        task.IsPublic = dto.IsPublic;
        Touch(task, userName);
        await _db.SaveChangesAsync(ct);
    }

    public async Task<TaskDetailDto> AddShare(string taskId, TaskShareInputDto dto, string userName, CancellationToken ct = default)
    {
        var task = await EnsureCreator(taskId, userName, ct);
        if (string.IsNullOrWhiteSpace(dto.UserName)) throw new ArgumentException("Vui lòng chọn người được chia sẻ.");
        if (!Enum.IsDefined(dto.Permission)) throw new ArgumentException("Quyền chia sẻ không hợp lệ.");
        var target = dto.UserName.Trim();
        if (target.Equals(userName, StringComparison.OrdinalIgnoreCase))
            throw new ArgumentException("Không thể chia sẻ công việc cho chính người tạo.");
        var account = await _db.AdAccounts.AsNoTracking().FirstOrDefaultAsync(a => a.UserName == target && a.IsActive, ct)
            ?? throw new ArgumentException($"Không tìm thấy tài khoản: {target}.");
        if (await _db.MeetingTaskShares.AsNoTracking().AnyAsync(s => s.TaskId == taskId && s.UserName == account.UserName, ct))
            throw new InvalidOperationException("Người này đã được chia sẻ công việc này.");

        var now = DateTime.UtcNow;
        _db.MeetingTaskShares.Add(new MeetingTaskShare
        {
            Id = Guid.NewGuid().ToString("N"),
            TaskId = taskId,
            UserName = account.UserName,
            Permission = (int)dto.Permission,
            CreateBy = userName,
            CreateDate = now,
            UpdateBy = userName,
            UpdateDate = now
        });
        Touch(task, userName);
        await _db.SaveChangesAsync(ct);
        return await ToDetail(task, userName, ct);
    }

    public async Task RemoveShare(string taskId, string targetUserName, string userName, CancellationToken ct = default)
    {
        await EnsureCreator(taskId, userName, ct);
        var share = await _db.MeetingTaskShares.FirstOrDefaultAsync(s => s.TaskId == taskId && s.UserName == targetUserName, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy người được chia sẻ.");
        _db.MeetingTaskShares.Remove(share);
        await _db.SaveChangesAsync(ct);
    }

    private IQueryable<MeetingTask> ViewableTasks(string userName) => _db.MeetingTasks.Where(t =>
        t.CreateBy == userName
        || t.IsPublic
        || t.AssigneeUserName == userName
        || _db.MeetingTaskShares.Any(s => s.TaskId == t.Id && s.UserName == userName)
        || (t.MeetingId != null && _db.MeetingPersonals.Any(p => p.MeetingId == t.MeetingId && p.UserName == userName)));

    private IQueryable<TaskListItemDto> ProjectList(IQueryable<MeetingTask> query, string userName) => query.Select(t => new TaskListItemDto
    {
        Id = t.Id,
        MeetingId = t.MeetingId,
        MeetingName = t.Meeting != null ? t.Meeting.Name : null,
        MeetingStartTime = t.Meeting != null ? (DateTime?)t.Meeting.ExpectedStartTime : null,
        ParentId = t.ParentId,
        Level = t.Level,
        ChildrenCount = 0,
        CompletedChildrenCount = 0,
        Progress = t.Status == (int)TaskStatus.Completed ? 100 : t.Status == (int)TaskStatus.InProgress ? 50 : 0,
        Title = t.Title,
        Description = t.Description,
        AssigneeUserName = t.AssigneeUserName,
        AssigneeFullName = _db.AdAccounts.Where(a => a.UserName == t.AssigneeUserName).Select(a => a.FullName).FirstOrDefault(),
        DueDate = t.DueDate,
        Status = (TaskStatus)t.Status,
        Priority = (TaskPriority)t.Priority,
        IsPublic = t.IsPublic,
        IsCreator = t.CreateBy == userName,
        CanEdit = t.CreateBy == userName || _db.MeetingTaskShares.Any(s => s.TaskId == t.Id && s.UserName == userName && s.Permission == (int)TaskSharePermission.Edit),
        CreateBy = t.CreateBy,
        CreateDate = t.CreateDate,
        UpdateDate = t.UpdateDate,
        SourceRef = t.SourceRef
    });

    private async Task<TaskDetailDto> ToDetail(MeetingTask task, string userName, CancellationToken ct)
    {
        var item = await ProjectList(_db.MeetingTasks.AsNoTracking().Where(t => t.Id == task.Id), userName).FirstOrDefaultAsync(ct)
            ?? throw new KeyNotFoundException("Không tìm thấy công việc.");
        var detail = new TaskDetailDto
        {
            Id = item.Id,
            MeetingId = item.MeetingId,
            MeetingName = item.MeetingName,
            MeetingStartTime = item.MeetingStartTime,
            ParentId = item.ParentId,
            Level = item.Level,
            ChildrenCount = item.ChildrenCount,
            CompletedChildrenCount = item.CompletedChildrenCount,
            Progress = item.Progress,
            Title = item.Title,
            Description = item.Description,
            AssigneeUserName = item.AssigneeUserName,
            AssigneeFullName = item.AssigneeFullName,
            DueDate = item.DueDate,
            Status = item.Status,
            Priority = item.Priority,
            IsPublic = item.IsPublic,
            IsCreator = item.IsCreator,
            CanEdit = item.CanEdit,
            CreateBy = item.CreateBy,
            CreateDate = item.CreateDate,
            UpdateDate = item.UpdateDate,
            SourceRef = item.SourceRef,
            Shares = new List<TaskShareDto>(),
            Children = new List<TaskListItemDto>()
        };
        if (task.CreateBy == userName)
            detail.Shares = await (from share in _db.MeetingTaskShares.AsNoTracking()
                                   join account in _db.AdAccounts.AsNoTracking() on share.UserName equals account.UserName into accounts
                                   from account in accounts.DefaultIfEmpty()
                                   where share.TaskId == task.Id
                                   orderby share.CreateDate
                                   select new TaskShareDto { UserName = share.UserName, FullName = account != null ? account.FullName : share.UserName, Permission = (TaskSharePermission)share.Permission }).ToListAsync(ct);
        detail.Children = await ProjectList(_db.MeetingTasks.AsNoTracking().Where(t => t.ParentId == task.Id), userName)
            .OrderBy(t => t.DueDate ?? DateTime.MaxValue).ThenByDescending(t => t.CreateDate).ToListAsync(ct);
        return detail;
    }

    private async Task<MeetingTask> EnsureCreator(string taskId, string userName, CancellationToken ct)
    {
        var task = await _db.MeetingTasks.FirstOrDefaultAsync(t => t.Id == taskId, ct)
            ?? throw new KeyNotFoundException("Không tìm thấy công việc.");
        if (task.CreateBy != userName)
            throw new UnauthorizedAccessException("Chỉ người tạo công việc mới được thay đổi thiết lập chia sẻ.");
        return task;
    }

    private static void ValidateEnums(TaskStatus status, TaskPriority priority)
    {
        if (!Enum.IsDefined(status)) throw new ArgumentException("Trạng thái công việc không hợp lệ.");
        if (!Enum.IsDefined(priority)) throw new ArgumentException("Mức ưu tiên không hợp lệ.");
    }

    private async Task<string?> ResolveAssignee(string? userName, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(userName)) return null;
        var name = userName.Trim();
        var account = await _db.AdAccounts.AsNoTracking().FirstOrDefaultAsync(a => a.UserName == name && a.IsActive, ct)
            ?? throw new ArgumentException($"Không tìm thấy tài khoản người phụ trách: {name}.");
        return account.UserName;
    }

    private async Task<bool> IsDescendant(string candidateParentId, string taskId, CancellationToken ct)
    {
        var cur = candidateParentId;
        for (int i = 0; i < 10; i++)
        {
            var p = await _db.MeetingTasks.AsNoTracking().Where(x => x.Id == cur).Select(x => x.ParentId).FirstOrDefaultAsync(ct);
            if (p == null) return false;
            if (p == taskId) return true;
            cur = p;
        }
        return false;
    }

    private async Task<int> GetMaxDescendantDepth(string taskId, CancellationToken ct)
    {
        var children = await _db.MeetingTasks.AsNoTracking().Where(x => x.ParentId == taskId).Select(x => x.Id).ToListAsync(ct);
        if (children.Count == 0) return 0;
        int max = 0;
        foreach (var c in children)
        {
            var d = await GetMaxDescendantDepth(c, ct);
            max = Math.Max(max, 1 + d);
        }
        return max;
    }

    private async Task PropagateLevel(string parentId, int parentLevel, CancellationToken ct)
    {
        var children = await _db.MeetingTasks.Where(x => x.ParentId == parentId).ToListAsync(ct);
        foreach (var c in children)
        {
            c.Level = parentLevel + 1;
            Touch(c, c.UpdateBy);
            await PropagateLevel(c.Id, c.Level, ct);
        }
        if (children.Count > 0) await _db.SaveChangesAsync(ct);
    }

    private static void Touch(MeetingTask task, string actor) { task.UpdateBy = actor; task.UpdateDate = DateTime.UtcNow; }
}
