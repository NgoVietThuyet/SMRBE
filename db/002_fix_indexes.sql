-- =====================================================================================
-- 002_fix_indexes.sql — SMR-DB §5 vấn đề D5, D6.
-- MeetingMessages.MeetingId và CmFiles.RefrenceFileId đang là nvarchar(max) nên không đánh index được,
-- mọi truy vấn chat/file theo cuộc họp đều quét toàn bảng.
-- Đổi sang nvarchar(450) (giá trị thực là GUID) rồi thêm index. Entity Java đã đổi cùng lúc (length = 450).
-- Idempotent. Script sẽ dừng nếu có dữ liệu dài hơn 450 ký tự (không cắt ngầm).
-- =====================================================================================
SET NOCOUNT ON;
GO

IF EXISTS (SELECT 1 FROM dbo.MeetingMessages WHERE LEN(MeetingId) > 450)
    THROW 50001, N'MeetingMessages.MeetingId có giá trị > 450 ký tự, kiểm tra dữ liệu trước khi chạy.', 1;
IF EXISTS (SELECT 1 FROM dbo.CmFiles WHERE LEN(RefrenceFileId) > 450)
    THROW 50002, N'CmFiles.RefrenceFileId có giá trị > 450 ký tự, kiểm tra dữ liệu trước khi chạy.', 1;
GO

IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID(N'dbo.MeetingMessages') AND name = N'MeetingId' AND max_length = -1)
    ALTER TABLE dbo.MeetingMessages ALTER COLUMN MeetingId nvarchar(450) NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_MeetingMessages_MeetingId_CreateDate'
               AND object_id = OBJECT_ID(N'dbo.MeetingMessages'))
    CREATE INDEX IX_MeetingMessages_MeetingId_CreateDate ON dbo.MeetingMessages (MeetingId, CreateDate);
GO

IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID(N'dbo.CmFiles') AND name = N'RefrenceFileId' AND max_length = -1)
    ALTER TABLE dbo.CmFiles ALTER COLUMN RefrenceFileId nvarchar(450) NOT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_CmFiles_RefrenceFileId_Type_OrderNumber'
               AND object_id = OBJECT_ID(N'dbo.CmFiles'))
    CREATE INDEX IX_CmFiles_RefrenceFileId_Type_OrderNumber ON dbo.CmFiles (RefrenceFileId, Type, OrderNumber);
GO
