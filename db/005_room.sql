-- =====================================================================================
-- 005_room.sql — P10 Phòng chờ và phòng họp.
--   MtAttendanceEvents : từng lần vào/rời phòng (tính thời lượng, xuất XLSX)
--   MtLobbyRequests    : hàng chờ phòng chờ (và lượt bị mời ra)
--   MtWhiteboards      : bảng trắng của cuộc họp, có Revision chống ghi đè
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE bản P10.
-- Idempotent: chạy lại nhiều lần không lỗi.
-- Ghi chú so với SMR-DB: nội dung bảng trắng lưu thẳng ở cột ContentJson (không đưa lên MinIO)
-- vì chỉ là danh sách nét vẽ dạng JSON, nhỏ và cần đọc/ghi nhanh.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.MtAttendanceEvents', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtAttendanceEvents (
        Id              nvarchar(450) NOT NULL CONSTRAINT PK_MtAttendanceEvents PRIMARY KEY,
        MeetingId       nvarchar(450) NOT NULL,
        UserName        nvarchar(450) NOT NULL,
        EventType       int           NOT NULL,          -- 0 vào · 1 rời
        OccurredAt      datetime2     NOT NULL,          -- UTC
        ClientSessionId nvarchar(100) NOT NULL
    );
    CREATE INDEX IX_MtAttendanceEvents_Meeting_User_Time ON dbo.MtAttendanceEvents (MeetingId, UserName, OccurredAt);
END
GO

IF OBJECT_ID(N'dbo.MtLobbyRequests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtLobbyRequests (
        Id          nvarchar(450) NOT NULL CONSTRAINT PK_MtLobbyRequests PRIMARY KEY,
        MeetingId   nvarchar(450) NOT NULL,
        UserName    nvarchar(450) NOT NULL,
        DisplayName nvarchar(200) NOT NULL,
        Status      int           NOT NULL,              -- 0 chờ · 1 cho vào · 2 từ chối / mời ra · 3 hết hạn
        RequestedAt datetime2     NOT NULL,
        DecidedBy   nvarchar(450) NULL,
        DecidedAt   datetime2     NULL
    );
    CREATE INDEX IX_MtLobbyRequests_Meeting_Status ON dbo.MtLobbyRequests (MeetingId, Status);
    CREATE INDEX IX_MtLobbyRequests_Meeting_User ON dbo.MtLobbyRequests (MeetingId, UserName, RequestedAt);
END
GO

IF OBJECT_ID(N'dbo.MtWhiteboards', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtWhiteboards (
        Id          nvarchar(450) NOT NULL CONSTRAINT PK_MtWhiteboards PRIMARY KEY,
        MeetingId   nvarchar(450) NOT NULL,
        Name        nvarchar(200) NOT NULL,
        Revision    int           NOT NULL CONSTRAINT DF_MtWhiteboards_Revision DEFAULT (0),
        ContentJson nvarchar(max) NOT NULL CONSTRAINT DF_MtWhiteboards_Content DEFAULT (N'[]'),
        IsLocked    bit           NOT NULL CONSTRAINT DF_MtWhiteboards_IsLocked DEFAULT (0),
        UpdatedBy   nvarchar(450) NULL,
        UpdatedAt   datetime2     NULL
    );
    CREATE UNIQUE INDEX UX_MtWhiteboards_MeetingId ON dbo.MtWhiteboards (MeetingId);
END
GO

PRINT N'005_room.sql: xong.';
GO
