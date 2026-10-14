-- =====================================================================================
-- 007_minutes.sql — P13 Biên bản họp.
--   MeetingTranscriptSegments : transcript lưu bền (trước đây nằm trong RAM, mất khi khởi động lại BE)
--   MtMinutes                 : biên bản theo phiên bản (nháp → chờ duyệt → đã duyệt → phát hành; bản phát hành bất biến)
--   SyBackgroundJobs          : job nền (sinh biên bản bằng AI) có IdempotencyKey chống tạo trùng
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE bản P13. Idempotent.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.MeetingTranscriptSegments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingTranscriptSegments (
        Id              nvarchar(100)  NOT NULL CONSTRAINT PK_MeetingTranscriptSegments PRIMARY KEY,
        MeetingId       nvarchar(450)  NOT NULL,
        SpeakerUserName nvarchar(450)  NULL,
        SpeakerName     nvarchar(200)  NULL,
        Text            nvarchar(max)  NOT NULL,
        Language        nvarchar(10)   NULL,
        SpokenAt        datetime2      NOT NULL,      -- UTC
        EditedBy        nvarchar(450)  NULL,
        EditedAt        datetime2      NULL
    );
    CREATE INDEX IX_MeetingTranscriptSegments_Meeting_Time ON dbo.MeetingTranscriptSegments (MeetingId, SpokenAt);
END
GO

IF OBJECT_ID(N'dbo.MtMinutes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtMinutes (
        Id             nvarchar(100)  NOT NULL CONSTRAINT PK_MtMinutes PRIMARY KEY,
        MeetingId      nvarchar(450)  NOT NULL,
        Version        int            NOT NULL,
        Status         int            NOT NULL,       -- 0 nháp · 1 chờ duyệt · 2 đã duyệt · 3 đã phát hành · 4 bị từ chối
        ContentJson    nvarchar(max)  NOT NULL,
        Source         nvarchar(20)   NOT NULL,       -- AI · MANUAL
        Model          nvarchar(100)  NULL,
        PromptVersion  nvarchar(50)   NULL,
        TranscriptHash nvarchar(64)   NULL,
        RejectReason   nvarchar(1000) NULL,
        CreatedBy      nvarchar(450)  NOT NULL,
        CreatedAt      datetime2      NOT NULL,
        UpdatedBy      nvarchar(450)  NULL,
        UpdatedAt      datetime2      NULL,
        SubmittedBy    nvarchar(450)  NULL,
        SubmittedAt    datetime2      NULL,
        ApprovedBy     nvarchar(450)  NULL,
        ApprovedAt     datetime2      NULL,
        PublishedBy    nvarchar(450)  NULL,
        PublishedAt    datetime2      NULL
    );
    CREATE UNIQUE INDEX UX_MtMinutes_Meeting_Version ON dbo.MtMinutes (MeetingId, Version);
END
GO

IF OBJECT_ID(N'dbo.SyBackgroundJobs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.SyBackgroundJobs (
        Id             nvarchar(100)  NOT NULL CONSTRAINT PK_SyBackgroundJobs PRIMARY KEY,
        Type           nvarchar(50)   NOT NULL,       -- MINUTES_GENERATE
        IdempotencyKey nvarchar(200)  NOT NULL,
        RefId          nvarchar(450)  NULL,           -- meetingId
        Status         int            NOT NULL,       -- 0 chờ · 1 đang chạy · 2 xong · 3 lỗi
        ResultRef      nvarchar(450)  NULL,           -- Id biên bản tạo ra
        Error          nvarchar(2000) NULL,
        CreatedBy      nvarchar(450)  NOT NULL,
        CreatedAt      datetime2      NOT NULL,
        StartedAt      datetime2      NULL,
        FinishedAt     datetime2      NULL
    );
    CREATE UNIQUE INDEX UX_SyBackgroundJobs_Key ON dbo.SyBackgroundJobs (IdempotencyKey);
    CREATE INDEX IX_SyBackgroundJobs_Ref ON dbo.SyBackgroundJobs (RefId, CreatedAt);
END
GO

PRINT N'007_minutes.sql: xong.';
GO
