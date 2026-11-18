-- =====================================================================================
-- 011_polls.sql — Biểu quyết trong cuộc họp.
--   MtPolls     : biểu quyết (câu hỏi, các phương án JSON, công khai/ẩn danh, mở/đóng).
--   MtPollVotes : phiếu bầu, mỗi người một phiếu trên mỗi biểu quyết (UserName luôn lưu để chặn bỏ phiếu hai lần;
--                 biểu quyết ẩn danh thì API không bao giờ trả ra).
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE có biểu quyết. Idempotent.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.MtPolls', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtPolls (
        Id          nvarchar(100)  NOT NULL CONSTRAINT PK_MtPolls PRIMARY KEY,
        MeetingId   nvarchar(450)  NOT NULL,
        Question    nvarchar(500)  NOT NULL,
        OptionsJson nvarchar(max)  NOT NULL,      -- mảng chuỗi các phương án
        Anonymous   bit            NOT NULL,
        Status      int            NOT NULL,      -- 0 mở · 1 đóng
        CreatedBy   nvarchar(450)  NOT NULL,
        CreatedAt   datetime2      NOT NULL,      -- UTC
        ClosedAt    datetime2      NULL
    );
    CREATE INDEX IX_MtPolls_Meeting ON dbo.MtPolls (MeetingId, CreatedAt);
END
GO

IF OBJECT_ID(N'dbo.MtPollVotes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtPollVotes (
        Id          nvarchar(100)  NOT NULL CONSTRAINT PK_MtPollVotes PRIMARY KEY,
        PollId      nvarchar(100)  NOT NULL,
        UserName    nvarchar(450)  NOT NULL,
        OptionIndex int            NOT NULL,
        VotedAt     datetime2      NOT NULL
    );
    CREATE UNIQUE INDEX UX_MtPollVotes_PollUser ON dbo.MtPollVotes (PollId, UserName);
END
GO
