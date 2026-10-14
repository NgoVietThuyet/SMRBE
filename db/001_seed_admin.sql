-- =====================================================================================
-- 001_seed_admin.sql — Bảo đảm có 1 tài khoản Admin (RoleCodes chứa SUPER_ADMIN).
-- BE chỉ coi là Admin khi RoleCodes chứa 'SUPER_ADMIN' (PermissionServiceImpl, AdminAccess).
-- Seed cũ seed_realistic_master_data.sql đặt admin là 'ADMIN,USER' → KHÔNG được nhận là Admin.
--
-- Hành vi (idempotent):
--   • Nếu đã có tài khoản 'admin'  → chỉ bổ sung SUPER_ADMIN vào RoleCodes, giữ nguyên mật khẩu.
--   • Nếu chưa có                   → tạo 'admin' trong đơn vị ORG_SYS, chức danh ADMIN (tạo nếu thiếu).
--     Mật khẩu tạm: Admin@123 (BCrypt bên dưới), MustChangePassword = 1 → bắt đổi ở lần đăng nhập đầu.
-- =====================================================================================
SET NOCOUNT ON;
GO

DECLARE @AdminHash nvarchar(max) = N'$2a$10$357H3B3hB6aI87wMIYy9/.FaVe8vO3Set3djmpR5uEvm8PAYXw0ge'; -- Admin@123

IF EXISTS (SELECT 1 FROM dbo.AdAccounts WHERE UserName = N'admin')
BEGIN
    UPDATE dbo.AdAccounts
    SET RoleCodes = CASE
                        WHEN RoleCodes IS NULL OR LTRIM(RTRIM(RoleCodes)) = N'' THEN N'SUPER_ADMIN'
                        WHEN UPPER(RoleCodes) LIKE N'%SUPER_ADMIN%' THEN RoleCodes
                        ELSE RoleCodes + N',SUPER_ADMIN'
                    END,
        IsActive = 1,
        UpdateBy = N'system',
        UpdateDate = SYSDATETIME()
    WHERE UserName = N'admin';
    PRINT N'Đã cấp SUPER_ADMIN cho tài khoản admin có sẵn.';
END
ELSE
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.MdOrganizes WHERE Id = N'ORG_SYS')
        INSERT INTO dbo.MdOrganizes (Id, PId, Name, OrderNumber, Expanded, PermissionJson, IsActive, Notes, CreateBy, CreateDate, UpdateBy, UpdateDate)
        VALUES (N'ORG_SYS', N'', N'Quản trị hệ thống', 0, 1, NULL, 1, N'Đơn vị của tài khoản quản trị', N'system', SYSDATETIME(), N'system', SYSDATETIME());

    IF NOT EXISTS (SELECT 1 FROM dbo.MdTitles WHERE Code = N'ADMIN')
        INSERT INTO dbo.MdTitles (Code, Name, Notes, OrderNumber, PermissionJson, IsActive, CreateBy, CreateDate, UpdateBy, UpdateDate)
        VALUES (N'ADMIN', N'Quản trị viên', N'Chức danh quản trị hệ thống', 0, NULL, 1, N'system', SYSDATETIME(), N'system', SYSDATETIME());

    INSERT INTO dbo.AdAccounts (UserName, Password, FullName, Phone, Email, Address, OrgId, TitleCode, PermissionJson, RoleCodes,
                                IsActive, MustChangePassword, LastLoginAt, TokenVersion, CreateBy, CreateDate, UpdateBy, UpdateDate)
    VALUES (N'admin', @AdminHash, N'Quản trị viên', N'', N'admin@smr.local', N'', N'ORG_SYS', N'ADMIN', NULL, N'SUPER_ADMIN',
            1, 1, NULL, 1, N'system', SYSDATETIME(), N'system', SYSDATETIME());
    PRINT N'Đã tạo tài khoản admin (mật khẩu tạm Admin@123, bắt đổi khi đăng nhập).';
END
GO
