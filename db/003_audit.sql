-- =====================================================================================
-- 003_audit.sql — P6 Nhật ký hoạt động: bảng SyAuditLogs (entity vn.com.d2s.smr.entity.sy.SyAuditLog).
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE có entity SyAuditLog.
-- Idempotent. Log chỉ được ghi thêm (không UPDATE/DELETE từ ứng dụng).
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.SyAuditLogs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.SyAuditLogs (
        Id            nvarchar(450) NOT NULL CONSTRAINT PK_SyAuditLogs PRIMARY KEY,
        Category      nvarchar(30)  NOT NULL,
        Action        nvarchar(100) NOT NULL,
        ActorUserName nvarchar(450) NULL,
        TargetType    nvarchar(50)  NULL,
        TargetId      nvarchar(450) NULL,
        Success       bit           NOT NULL,
        HttpStatus    int           NOT NULL,
        Ip            nvarchar(64)  NULL,
        UserAgent     nvarchar(500) NULL,
        PayloadJson   nvarchar(max) NULL,
        OccurredAt    datetime2     NOT NULL
    );
    CREATE INDEX IX_SyAuditLogs_OccurredAt ON dbo.SyAuditLogs (OccurredAt DESC);
    CREATE INDEX IX_SyAuditLogs_ActorUserName ON dbo.SyAuditLogs (ActorUserName);
    CREATE INDEX IX_SyAuditLogs_Category ON dbo.SyAuditLogs (Category);
END
GO
