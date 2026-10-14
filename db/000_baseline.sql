-- =====================================================================================
-- 000_baseline.sql — Schema gốc của SMR (10 bảng nghiệp vụ; Notifications ở 2026_10_notifications.sql)
-- Sinh từ các entity JPA trong BE-Java (ddl-auto: validate) ngày 09/10/2026.
-- Idempotent: bảng nào đã có thì bỏ qua → chạy được trên DB đang dùng lẫn DB trống.
-- Lưu ý: script KHÔNG sửa bảng đã tồn tại. Nếu DB thật lệch với file này, BE sẽ báo lỗi validate khi khởi động;
--        khi đó xuất lại schema thật và cập nhật file này cho khớp.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.MdOrganizes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MdOrganizes (
        Id             nvarchar(450) NOT NULL CONSTRAINT PK_MdOrganizes PRIMARY KEY,
        PId            nvarchar(450) NOT NULL,
        Name           nvarchar(max) NOT NULL,
        OrderNumber    int           NOT NULL,
        Expanded       bit           NOT NULL,
        PermissionJson nvarchar(max) NULL,
        IsActive       bit           NOT NULL CONSTRAINT DF_MdOrganizes_IsActive DEFAULT (1),
        Notes          nvarchar(max) NULL,
        CreateBy       nvarchar(max) NOT NULL,
        CreateDate     datetime2     NULL,
        UpdateBy       nvarchar(max) NOT NULL,
        UpdateDate     datetime2     NULL
    );
    CREATE INDEX IX_MdOrganizes_PId ON dbo.MdOrganizes (PId);
END
GO

IF OBJECT_ID(N'dbo.MdTitles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MdTitles (
        Code           nvarchar(450) NOT NULL CONSTRAINT PK_MdTitles PRIMARY KEY,
        Name           nvarchar(max) NOT NULL,
        Notes          nvarchar(max) NOT NULL,
        OrderNumber    int           NOT NULL,
        PermissionJson nvarchar(max) NULL,
        IsActive       bit           NOT NULL CONSTRAINT DF_MdTitles_IsActive DEFAULT (1),
        CreateBy       nvarchar(max) NOT NULL,
        CreateDate     datetime2     NULL,
        UpdateBy       nvarchar(max) NOT NULL,
        UpdateDate     datetime2     NULL
    );
END
GO

IF OBJECT_ID(N'dbo.AdAccounts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AdAccounts (
        UserName           nvarchar(450) NOT NULL CONSTRAINT PK_AdAccounts PRIMARY KEY,
        Password           nvarchar(max) NOT NULL,
        FullName           nvarchar(max) NOT NULL,
        Phone              nvarchar(max) NOT NULL,
        Email              nvarchar(255) NOT NULL,
        Address            nvarchar(max) NOT NULL,
        OrgId              nvarchar(450) NOT NULL,
        TitleCode          nvarchar(450) NOT NULL,
        PermissionJson     nvarchar(max) NULL,
        RoleCodes          nvarchar(max) NULL,
        IsActive           bit           NOT NULL CONSTRAINT DF_AdAccounts_IsActive DEFAULT (1),
        MustChangePassword bit           NOT NULL CONSTRAINT DF_AdAccounts_MustChangePassword DEFAULT (1),
        LastLoginAt        datetime2     NULL,
        TokenVersion       int           NOT NULL CONSTRAINT DF_AdAccounts_TokenVersion DEFAULT (1),
        CreateBy           nvarchar(max) NOT NULL,
        CreateDate         datetime2     NULL,
        UpdateBy           nvarchar(max) NOT NULL,
        UpdateDate         datetime2     NULL
    );
    CREATE UNIQUE INDEX IX_AdAccounts_Email ON dbo.AdAccounts (Email);
    CREATE INDEX IX_AdAccounts_OrgId ON dbo.AdAccounts (OrgId);
    CREATE INDEX IX_AdAccounts_TitleCode ON dbo.AdAccounts (TitleCode);
END
GO

IF OBJECT_ID(N'dbo.MeetingInfos', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingInfos (
        Id                 nvarchar(450) NOT NULL CONSTRAINT PK_MeetingInfos PRIMARY KEY,
        Name               nvarchar(max) NOT NULL,
        ExpectedStartTime  datetime2     NOT NULL,
        StartDate          datetime2     NULL,
        EndDate            datetime2     NULL,
        MeetContent        nvarchar(max) NOT NULL,
        Status             int           NOT NULL,
        Notes              nvarchar(max) NOT NULL,
        RefrenceFileId     nvarchar(max) NOT NULL,
        ExpectedEndTime    datetime2     NULL,
        TimeZone           nvarchar(max) NOT NULL,
        Agenda             nvarchar(max) NOT NULL,
        Visibility         int           NOT NULL,
        RoomCode           nvarchar(450) NOT NULL,
        JoinUrl            nvarchar(max) NOT NULL,
        SettingsJson       nvarchar(max) NOT NULL,
        CancellationReason nvarchar(max) NOT NULL,
        IsDraft            bit           NOT NULL,
        IsArchived         bit           NOT NULL,
        IsDeleted          bit           NOT NULL,
        Version            int           NOT NULL,
        RowVersion         rowversion    NOT NULL,
        CreateBy           nvarchar(max) NOT NULL,
        CreateDate         datetime2     NULL,
        UpdateBy           nvarchar(max) NOT NULL,
        UpdateDate         datetime2     NULL
    );
    CREATE UNIQUE INDEX IX_MeetingInfos_RoomCode ON dbo.MeetingInfos (RoomCode);
    CREATE INDEX IX_MeetingInfos_Status_ExpectedStartTime ON dbo.MeetingInfos (Status, ExpectedStartTime);
END
GO

IF OBJECT_ID(N'dbo.MeetingPersonals', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingPersonals (
        Id             nvarchar(450) NOT NULL CONSTRAINT PK_MeetingPersonals PRIMARY KEY,
        MeetingId      nvarchar(450) NOT NULL,
        UserName       nvarchar(450) NOT NULL,
        FullName       nvarchar(max) NOT NULL,
        Phone          nvarchar(max) NOT NULL,
        Email          nvarchar(max) NOT NULL,
        Address        nvarchar(max) NOT NULL,
        OrgId          nvarchar(max) NOT NULL,
        TitleCode      nvarchar(max) NOT NULL,
        RefrenceFileId nvarchar(max) NOT NULL,
        Type           int           NOT NULL,
        IsChuTri       bit           NOT NULL,
        IsJoined       bit           NOT NULL,
        JoinTime       datetime2     NULL,
        CreateBy       nvarchar(max) NOT NULL,
        CreateDate     datetime2     NULL,
        UpdateBy       nvarchar(max) NOT NULL,
        UpdateDate     datetime2     NULL
    );
    CREATE UNIQUE INDEX IX_MeetingPersonals_MeetingId_UserName ON dbo.MeetingPersonals (MeetingId, UserName);
END
GO

IF OBJECT_ID(N'dbo.MeetingMessages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingMessages (
        Id             nvarchar(450) NOT NULL CONSTRAINT PK_MeetingMessages PRIMARY KEY,
        MeetingId      nvarchar(max) NOT NULL,
        SenderUserId   nvarchar(max) NOT NULL,
        ReceiverUserId nvarchar(max) NOT NULL,
        MessageText    nvarchar(max) NOT NULL,
        CreateBy       nvarchar(max) NOT NULL,
        CreateDate     datetime2     NULL,
        UpdateBy       nvarchar(max) NOT NULL,
        UpdateDate     datetime2     NULL
    );
END
GO

IF OBJECT_ID(N'dbo.MeetingAuditLogs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingAuditLogs (
        Id            nvarchar(450) NOT NULL CONSTRAINT PK_MeetingAuditLogs PRIMARY KEY,
        MeetingId     nvarchar(450) NOT NULL,
        Action        nvarchar(max) NOT NULL,
        ActorId       nvarchar(max) NOT NULL,
        OccurredAt    datetime2     NOT NULL,
        CorrelationId nvarchar(max) NOT NULL,
        Version       int           NOT NULL,
        PayloadJson   nvarchar(max) NOT NULL
    );
    CREATE INDEX IX_MeetingAuditLogs_MeetingId_OccurredAt ON dbo.MeetingAuditLogs (MeetingId, OccurredAt);
END
GO

IF OBJECT_ID(N'dbo.MeetingTasks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingTasks (
        Id               nvarchar(450)  NOT NULL CONSTRAINT PK_MeetingTasks PRIMARY KEY,
        MeetingId        nvarchar(450)  NULL,
        ParentId         nvarchar(450)  NULL,
        Level            int            NOT NULL,
        Title            nvarchar(300)  NOT NULL,
        Description      nvarchar(4000) NULL,
        AssigneeUserName nvarchar(450)  NULL,
        DueDate          datetime2      NULL,
        Status           int            NOT NULL,
        Priority         int            NOT NULL,
        IsPublic         bit            NOT NULL,
        SourceRef        nvarchar(max)  NULL,
        CreateBy         nvarchar(max)  NOT NULL,
        CreateDate       datetime2      NULL,
        UpdateBy         nvarchar(max)  NOT NULL,
        UpdateDate       datetime2      NULL
    );
    CREATE INDEX IX_MeetingTasks_MeetingId ON dbo.MeetingTasks (MeetingId);
    CREATE INDEX IX_MeetingTasks_AssigneeUserName ON dbo.MeetingTasks (AssigneeUserName);
    CREATE INDEX IX_MeetingTasks_DueDate ON dbo.MeetingTasks (DueDate);
    CREATE INDEX IX_MeetingTasks_ParentId ON dbo.MeetingTasks (ParentId);
    CREATE INDEX IX_MeetingTasks_Level ON dbo.MeetingTasks (Level);
END
GO

IF OBJECT_ID(N'dbo.MeetingTaskShares', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MeetingTaskShares (
        Id         nvarchar(450) NOT NULL CONSTRAINT PK_MeetingTaskShares PRIMARY KEY,
        TaskId     nvarchar(450) NOT NULL,
        UserName   nvarchar(450) NOT NULL,
        Permission int           NOT NULL,
        CreateBy   nvarchar(max) NOT NULL,
        CreateDate datetime2     NULL,
        UpdateBy   nvarchar(max) NOT NULL,
        UpdateDate datetime2     NULL
    );
    CREATE UNIQUE INDEX IX_MeetingTaskShares_TaskId_UserName ON dbo.MeetingTaskShares (TaskId, UserName);
END
GO

IF OBJECT_ID(N'dbo.CmFiles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CmFiles (
        Id             nvarchar(450)  NOT NULL CONSTRAINT PK_CmFiles PRIMARY KEY,
        FileName       nvarchar(max)  NOT NULL,
        FileSize       decimal(18, 2) NOT NULL,
        MimeType       nvarchar(max)  NOT NULL,
        Extention      nvarchar(max)  NOT NULL,
        Type           int            NOT NULL,
        Icon           nvarchar(max)  NOT NULL,
        RefrenceFileId nvarchar(max)  NOT NULL,
        IsBienBan      bit            NOT NULL,
        OrderNumber    int            NOT NULL,
        VoiceToText    nvarchar(max)  NOT NULL,
        BucketName     nvarchar(max)  NOT NULL,
        ObjectName     nvarchar(max)  NOT NULL,
        CreateBy       nvarchar(max)  NOT NULL,
        CreateDate     datetime2      NULL,
        UpdateBy       nvarchar(max)  NOT NULL,
        UpdateDate     datetime2      NULL
    );
END
GO
