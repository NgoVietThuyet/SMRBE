-- =====================================================================================
-- 008_meeting_record.sql — Hồ sơ cuộc họp (chốt khi họp kết thúc).
--   MtMeetingRecords : mỗi cuộc họp một dòng; SnapshotJson = ảnh chụp cố định của cuộc họp lúc chốt
--                      (tên, giờ, thành phần, transcript, tài liệu). Sinh biên bản bằng AI và dựng file Word
--                      đọc từ đây nên không đổi khi cuộc họp / thành phần bị sửa về sau.
--                      Revision tăng mỗi lần chốt lại (sửa transcript, sinh lại biên bản khi nguồn đã đổi).
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE bản có hồ sơ chốt. Idempotent.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.MtMeetingRecords', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtMeetingRecords (
        Id           nvarchar(100) NOT NULL CONSTRAINT PK_MtMeetingRecords PRIMARY KEY,
        MeetingId    nvarchar(450) NOT NULL,
        Revision     int           NOT NULL,
        SourceHash   nvarchar(64)  NOT NULL,          -- SHA-256 của SnapshotJson: đổi nội dung mới tăng Revision
        SnapshotJson nvarchar(max) NOT NULL,
        SealedBy     nvarchar(450) NOT NULL,          -- tài khoản chốt; 'system' khi tự chốt lúc họp kết thúc
        SealedAt     datetime2     NOT NULL           -- UTC
    );
    CREATE UNIQUE INDEX UX_MtMeetingRecords_Meeting ON dbo.MtMeetingRecords (MeetingId);
END
GO
