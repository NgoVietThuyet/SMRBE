-- =====================================================================================
-- 009_face.sql — Đăng nhập / vào họp bằng khuôn mặt.
--   AdFaceTemplates : đặc trưng khuôn mặt (embedding) của người dùng, MÃ HÓA AES-256-GCM (nonce 12 byte | dữ liệu | tag 16 byte).
--                     Không lưu ảnh gốc. Mỗi người có vài dòng (nhiều góc mặt khi đăng ký).
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE có tính năng khuôn mặt. Idempotent.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.AdFaceTemplates', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AdFaceTemplates (
        Id        nvarchar(100)  NOT NULL CONSTRAINT PK_AdFaceTemplates PRIMARY KEY,
        UserName  nvarchar(450)  NOT NULL,
        Embedding varbinary(max) NOT NULL,         -- mã hóa AES-GCM
        Model     nvarchar(50)   NOT NULL,         -- ví dụ ArcFace
        Dim       int            NOT NULL,         -- số chiều (ArcFace = 512)
        Quality   float          NOT NULL,         -- 0..1 lúc đăng ký
        CreatedAt datetime2      NOT NULL,         -- UTC
        IsActive  bit            NOT NULL CONSTRAINT DF_AdFaceTemplates_IsActive DEFAULT 1
    );
    CREATE INDEX IX_AdFaceTemplates_User ON dbo.AdFaceTemplates (UserName);
END
GO
