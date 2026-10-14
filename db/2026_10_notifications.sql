-- =====================================================
-- Thông báo người dùng (nút Chuông) — Document/Plan_ThongBao.md §3
-- Chạy tay trên DB trước khi khởi động BE (ddl-auto: validate).
-- Idempotent: chạy lại không lỗi. Khóa index ≤ 1700 byte (SQL Server): 450*2 + 300*2.
-- =====================================================
IF OBJECT_ID(N'dbo.Notifications', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Notifications (
        Id                nvarchar(450)  NOT NULL CONSTRAINT PK_Notifications PRIMARY KEY,
        RecipientUserName nvarchar(450)  NOT NULL,
        Type              varchar(50)    NOT NULL,
        Severity          tinyint        NOT NULL CONSTRAINT DF_Notifications_Severity DEFAULT (0),
        Title             nvarchar(300)  NOT NULL,
        Body              nvarchar(1000) NULL,
        ResourceType      varchar(30)    NOT NULL,
        ResourceId        nvarchar(450)  NULL,
        MeetingId         nvarchar(450)  NULL,
        ActorUserName     nvarchar(450)  NULL,
        ActorFullName     nvarchar(300)  NULL,
        DataJson          nvarchar(max)  NULL,
        DedupeKey         nvarchar(300)  NOT NULL,
        IsRead            bit            NOT NULL CONSTRAINT DF_Notifications_IsRead DEFAULT (0),
        ReadAt            datetime2      NULL,
        CreateDate        datetime2      NOT NULL
    );

    CREATE INDEX IX_Notifications_Recipient_Created
        ON dbo.Notifications (RecipientUserName, CreateDate DESC) INCLUDE (IsRead);

    CREATE UNIQUE INDEX UX_Notifications_Recipient_Dedupe
        ON dbo.Notifications (RecipientUserName, DedupeKey);
END
GO
