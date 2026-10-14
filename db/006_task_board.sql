-- =====================================================================================
-- 006_task_board.sql — P12 Công việc (giao diện bảng kiểu Trello theo module "Quản lý công việc" của 2.FrontEnd).
--   MeetingTasks         + StartDate, SortOrder, Color, IsArchived
--   MtTaskLabels         : nhãn của một công việc cấp 0 (bảng)
--   MtTaskCardLabels     : nhãn gắn vào việc
--   MtTaskChecklistItems : checklist của việc
--   MtTaskComments       : bình luận
--   File đính kèm công việc dùng CmFiles (Type = 3, RefrenceFileId = Id việc) — không đổi cấu trúc.
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE bản P12.
-- Idempotent: chạy lại nhiều lần không lỗi.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF COL_LENGTH(N'dbo.MeetingTasks', N'StartDate') IS NULL
    ALTER TABLE dbo.MeetingTasks ADD StartDate datetime2 NULL;
GO
IF COL_LENGTH(N'dbo.MeetingTasks', N'SortOrder') IS NULL
    ALTER TABLE dbo.MeetingTasks ADD SortOrder int NOT NULL CONSTRAINT DF_MeetingTasks_SortOrder DEFAULT (0);
GO
IF COL_LENGTH(N'dbo.MeetingTasks', N'Color') IS NULL
    ALTER TABLE dbo.MeetingTasks ADD Color nvarchar(20) NULL;
GO
IF COL_LENGTH(N'dbo.MeetingTasks', N'IsArchived') IS NULL
    ALTER TABLE dbo.MeetingTasks ADD IsArchived bit NOT NULL CONSTRAINT DF_MeetingTasks_IsArchived DEFAULT (0);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_MeetingTasks_ParentId_SortOrder' AND object_id = OBJECT_ID(N'dbo.MeetingTasks'))
    CREATE INDEX IX_MeetingTasks_ParentId_SortOrder ON dbo.MeetingTasks (ParentId, SortOrder);
GO

-- Thứ tự ban đầu cho dữ liệu cũ: theo ngày tạo trong từng cha.
;WITH ordered AS (
    SELECT Id, ROW_NUMBER() OVER (PARTITION BY ParentId ORDER BY CreateDate, Id) - 1 AS rn
    FROM dbo.MeetingTasks
    WHERE ParentId IS NOT NULL
)
UPDATE t SET SortOrder = o.rn
FROM dbo.MeetingTasks t JOIN ordered o ON o.Id = t.Id
WHERE t.SortOrder = 0 AND o.rn > 0;
GO

IF OBJECT_ID(N'dbo.MtTaskLabels', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtTaskLabels (
        Id     nvarchar(450) NOT NULL CONSTRAINT PK_MtTaskLabels PRIMARY KEY,
        WorkId nvarchar(450) NOT NULL,       -- công việc cấp 0 (bảng)
        Name   nvarchar(50)  NOT NULL,
        Color  nvarchar(20)  NOT NULL
    );
    CREATE INDEX IX_MtTaskLabels_WorkId ON dbo.MtTaskLabels (WorkId);
END
GO

IF OBJECT_ID(N'dbo.MtTaskCardLabels', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtTaskCardLabels (
        TaskId  nvarchar(450) NOT NULL,
        LabelId nvarchar(450) NOT NULL,
        CONSTRAINT PK_MtTaskCardLabels PRIMARY KEY (TaskId, LabelId)
    );
    CREATE INDEX IX_MtTaskCardLabels_LabelId ON dbo.MtTaskCardLabels (LabelId);
END
GO

IF OBJECT_ID(N'dbo.MtTaskChecklistItems', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtTaskChecklistItems (
        Id        nvarchar(450) NOT NULL CONSTRAINT PK_MtTaskChecklistItems PRIMARY KEY,
        TaskId    nvarchar(450) NOT NULL,
        Content   nvarchar(500) NOT NULL,
        IsDone    bit           NOT NULL CONSTRAINT DF_MtTaskChecklistItems_IsDone DEFAULT (0),
        SortOrder int           NOT NULL CONSTRAINT DF_MtTaskChecklistItems_SortOrder DEFAULT (0)
    );
    CREATE INDEX IX_MtTaskChecklistItems_Task ON dbo.MtTaskChecklistItems (TaskId, SortOrder);
END
GO

IF OBJECT_ID(N'dbo.MtTaskComments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtTaskComments (
        Id         nvarchar(450)  NOT NULL CONSTRAINT PK_MtTaskComments PRIMARY KEY,
        TaskId     nvarchar(450)  NOT NULL,
        UserName   nvarchar(450)  NOT NULL,
        Content    nvarchar(2000) NOT NULL,
        CreateDate datetime2      NOT NULL       -- UTC
    );
    CREATE INDEX IX_MtTaskComments_Task ON dbo.MtTaskComments (TaskId, CreateDate);
END
GO

PRINT N'006_task_board.sql: xong.';
GO
