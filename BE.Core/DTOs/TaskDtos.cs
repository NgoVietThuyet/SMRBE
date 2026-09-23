using System.ComponentModel.DataAnnotations;

namespace BE.Core.DTOs;

// Trạng thái công việc — spec mục 12 (3 mức, không thêm workflow mới)
public enum TaskStatus { NotStarted = 0, InProgress = 1, Completed = 2 }
// Ưu tiên công việc
public enum TaskPriority { Low = 0, Medium = 1, High = 2 }
// Quyền chia sẻ công việc
public enum TaskSharePermission { View = 0, Edit = 1 }

public sealed class TaskSearchDto
{
    public string Shortcut { get; set; } = "all"; // all | mine | overdue | today
    public string Keyword { get; set; } = string.Empty;
    public string? MeetingId { get; set; }
    public string? Assignee { get; set; } // userName | "none" (chưa gán)
    public TaskStatus? Status { get; set; }
    public TaskPriority? Priority { get; set; }
    public int? Level { get; set; } // 0=Work, 1=SubWork, 2=Task (null = all)
    public string? ParentId { get; set; } // filter children of parent
    public int Page { get; set; } = 1;
    public int PageSize { get; set; } = 50;
}

// Số đếm cho các chip Tất cả/Của tôi/Quá hạn/Hôm nay — tính trên bộ lọc cơ sở
public sealed class TaskSummaryDto
{
    public int Total { get; set; }
    public int Mine { get; set; }
    public int Overdue { get; set; }
    public int Today { get; set; }
}

public sealed class TaskSearchResultDto
{
    public List<TaskListItemDto> Items { get; set; } = new();
    public int TotalItems { get; set; }
    public int TotalPages { get; set; }
    public int Page { get; set; }
    public int PageSize { get; set; }
    public TaskSummaryDto Summary { get; set; } = new();
}

public class TaskListItemDto
{
    public string Id { get; set; } = string.Empty;
    public string? MeetingId { get; set; }
    public string? MeetingName { get; set; }   // null với công việc riêng
    public DateTime? MeetingStartTime { get; set; } // ExpectedStartTime của cuộc họp (header group)
    public string? ParentId { get; set; }
    public int Level { get; set; } // 0=Work, 1=SubWork, 2=Task
    public int ChildrenCount { get; set; }
    public int CompletedChildrenCount { get; set; }
    public int Progress { get; set; } // 0-100
    public string Title { get; set; } = string.Empty;
    public string? Description { get; set; }
    public string? AssigneeUserName { get; set; }
    public string? AssigneeFullName { get; set; }
    public DateTime? DueDate { get; set; }
    public TaskStatus Status { get; set; }
    public TaskPriority Priority { get; set; }
    public bool IsPublic { get; set; }
    public bool IsCreator { get; set; } // requester là người tạo
    public bool CanEdit { get; set; }   // requester được sửa nội dung
    public string CreateBy { get; set; } = string.Empty;
    public DateTime? CreateDate { get; set; }
    public DateTime? UpdateDate { get; set; }
    public string? SourceRef { get; set; }
}

public sealed class TaskDetailDto : TaskListItemDto
{
    public List<TaskShareDto> Shares { get; set; } = new(); // chỉ đổ khi requester là creator
    public List<TaskListItemDto> Children { get; set; } = new(); // SubWork/Task con cấp 1
}

public sealed class TaskShareDto
{
    public string UserName { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;
    public TaskSharePermission Permission { get; set; }
}

public sealed class CreateTaskDto
{
    public string? MeetingId { get; set; } // null = công việc riêng
    public string? ParentId { get; set; } // null = Work lớn
    public int Level { get; set; } // 0-2, tự tính từ Parent nếu có
    [Required, StringLength(300)] public string Title { get; set; } = string.Empty;
    [StringLength(4000)] public string? Description { get; set; }
    public string? AssigneeUserName { get; set; }
    public DateTime? DueDate { get; set; }
    public TaskStatus Status { get; set; }
    public TaskPriority Priority { get; set; }
    public bool IsPublic { get; set; }
    public string? SourceRef { get; set; }
}

public sealed class UpdateTaskDto
{
    [Required, StringLength(300)] public string Title { get; set; } = string.Empty;
    [StringLength(4000)] public string? Description { get; set; }
    public string? AssigneeUserName { get; set; }
    public DateTime? DueDate { get; set; }
    public TaskStatus Status { get; set; }
    public TaskPriority Priority { get; set; }
    public string? SourceRef { get; set; }
    public string? ParentId { get; set; }
    public int? Level { get; set; }
}

public sealed class TaskVisibilityDto
{
    public bool IsPublic { get; set; }
}

public sealed class TaskShareInputDto
{
    [Required] public string UserName { get; set; } = string.Empty;
    public TaskSharePermission Permission { get; set; }
}

public sealed class UpdateTaskStatusDto
{
    public TaskStatus Status { get; set; }
}

