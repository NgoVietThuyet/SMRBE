-- =====================================================================================
-- 004_settings.sql — P7 Cấu hình hệ thống: bảng SySettings (entity vn.com.d2s.smr.entity.sy.SySetting).
-- BE dùng ddl-auto: validate → PHẢI chạy script này trước khi khởi động BE có entity SySetting.
-- Mỗi khóa một dòng, giá trị JSON. Khóa chưa có dòng → BE dùng giá trị mặc định trong SettingServiceImpl.
--   file.maxUploadSizeMb, file.allowedExtensions, recording.retentionDays, minutes.defaultTemplateId
-- Mẫu biên bản DOCX lưu ở CmFiles với Type = 5, RefrenceFileId = 'MINUTES_TEMPLATE'.
-- Idempotent.
-- =====================================================================================
SET NOCOUNT ON;
GO

IF OBJECT_ID(N'dbo.SySettings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.SySettings (
        SettingKey nvarchar(100) NOT NULL CONSTRAINT PK_SySettings PRIMARY KEY,
        ValueJson  nvarchar(max) NOT NULL,
        UpdatedBy  nvarchar(450) NULL,
        UpdatedAt  datetime2     NULL
    );
END
GO
