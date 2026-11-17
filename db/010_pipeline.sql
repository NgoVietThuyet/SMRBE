-- =====================================================================================
-- 010_pipeline.sql — Pipeline xử lý sau cuộc họp (làm sạch transcript, trích quyết định, chỉ mục hỏi đáp RAG).
--   MtMeetingAnalyses : kết quả phân tích mỗi cuộc họp (từ điển từ khóa, transcript đã làm sạch, tóm tắt tài liệu).
--   MtRagChunks       : các đoạn dữ liệu (transcript cửa sổ trượt, tài liệu theo trang / đề mục) + vector embedding (nếu có).
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE có pipeline. Idempotent.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.MtMeetingAnalyses', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtMeetingAnalyses (
        Id             nvarchar(100)  NOT NULL CONSTRAINT PK_MtMeetingAnalyses PRIMARY KEY,
        MeetingId      nvarchar(450)  NOT NULL,
        SourceHash     nvarchar(64)   NOT NULL,     -- băm nguồn (transcript + tài liệu): đổi thì phân tích lại
        GlossaryJson   nvarchar(max)  NOT NULL,     -- từ điển từ khóa lấy từ tài liệu + thành viên
        DocDigestJson  nvarchar(max)  NOT NULL,     -- tóm tắt từng tài liệu
        CleanedJson    nvarchar(max)  NOT NULL,     -- transcript đã làm sạch (giữ ánh xạ về đoạn gốc)
        Model          nvarchar(100)  NULL,
        CreatedAt      datetime2      NOT NULL,     -- UTC
        UpdatedAt      datetime2      NOT NULL
    );
    CREATE UNIQUE INDEX UX_MtMeetingAnalyses_Meeting ON dbo.MtMeetingAnalyses (MeetingId);
END
GO

IF OBJECT_ID(N'dbo.MtRagChunks', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.MtRagChunks (
        Id         nvarchar(100)  NOT NULL CONSTRAINT PK_MtRagChunks PRIMARY KEY,
        MeetingId  nvarchar(450)  NOT NULL,
        Kind       nvarchar(20)   NOT NULL,         -- TRANSCRIPT · DOC
        Title      nvarchar(300)  NULL,
        Speaker    nvarchar(300)  NULL,
        OffsetText nvarchar(20)   NULL,             -- mốc mm:ss từ lúc họp bắt đầu (transcript)
        Content    nvarchar(max)  NOT NULL,
        Refs       nvarchar(max)  NULL,             -- Id đoạn transcript gốc (cách nhau dấu phẩy) hoặc tên tài liệu + trang
        Embedding  varbinary(max) NULL,             -- float32 little-endian, đã chuẩn hóa độ dài 1
        Ord        int            NOT NULL,
        CreatedAt  datetime2      NOT NULL
    );
    CREATE INDEX IX_MtRagChunks_Meeting ON dbo.MtRagChunks (MeetingId, Ord);
END
GO
