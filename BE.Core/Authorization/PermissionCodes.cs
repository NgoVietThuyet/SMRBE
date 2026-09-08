namespace BE.Core.Authorization
{
    public static class PermissionCodes
    {
        public const string HrView = "HR_VIEW", HrOrgCreate = "HR_ORG_CREATE", HrOrgUpdate = "HR_ORG_UPDATE", HrOrgMove = "HR_ORG_MOVE", HrOrgDelete = "HR_ORG_DELETE", HrOrgPermission = "HR_ORG_PERMISSION";
        public const string HrTitleView = "HR_TITLE_VIEW", HrTitleCreate = "HR_TITLE_CREATE", HrTitleUpdate = "HR_TITLE_UPDATE", HrTitleDelete = "HR_TITLE_DELETE", HrTitlePermission = "HR_TITLE_PERMISSION";
        public const string HrAccountView = "HR_ACCOUNT_VIEW", HrAccountCreate = "HR_ACCOUNT_CREATE", HrAccountUpdate = "HR_ACCOUNT_UPDATE", HrAccountLock = "HR_ACCOUNT_LOCK", HrAccountResetPassword = "HR_ACCOUNT_RESET_PASSWORD", HrAccountTransfer = "HR_ACCOUNT_TRANSFER", HrAccountChangeTitle = "HR_ACCOUNT_CHANGE_TITLE", HrAccountPermission = "HR_ACCOUNT_PERMISSION";
        public const string MeetingCreate = "MEETING_CREATE", MeetingInvite = "MEETING_INVITE", MeetingRecord = "MEETING_RECORD", FileUpload = "FILE_UPLOAD", FileDownload = "FILE_DOWNLOAD", AiSummary = "AI_SUMMARY", DashboardView = "DASHBOARD_VIEW";
        public static readonly IReadOnlyList<PermissionDefinition> Catalog = new List<PermissionDefinition>
        {
            new(HrView,"Xem module nhân sự","Nhân sự"), new(HrOrgCreate,"Tạo phòng ban","Cơ cấu tổ chức"), new(HrOrgUpdate,"Cập nhật phòng ban","Cơ cấu tổ chức"), new(HrOrgMove,"Di chuyển phòng ban","Cơ cấu tổ chức"), new(HrOrgDelete,"Xóa phòng ban","Cơ cấu tổ chức"), new(HrOrgPermission,"Phân quyền phòng ban","Cơ cấu tổ chức"),
            new(HrTitleView,"Xem chức danh","Chức danh"), new(HrTitleCreate,"Tạo chức danh","Chức danh"), new(HrTitleUpdate,"Cập nhật chức danh","Chức danh"), new(HrTitleDelete,"Xóa chức danh","Chức danh"), new(HrTitlePermission,"Phân quyền chức danh","Chức danh"),
            new(HrAccountView,"Xem tài khoản","Tài khoản"), new(HrAccountCreate,"Tạo tài khoản","Tài khoản"), new(HrAccountUpdate,"Cập nhật tài khoản","Tài khoản"), new(HrAccountLock,"Khóa/mở tài khoản","Tài khoản"), new(HrAccountResetPassword,"Đặt lại mật khẩu","Tài khoản"), new(HrAccountTransfer,"Điều chuyển phòng ban","Tài khoản"), new(HrAccountChangeTitle,"Đổi chức danh","Tài khoản"), new(HrAccountPermission,"Phân quyền tài khoản","Tài khoản"),
            new(MeetingCreate,"Tạo cuộc họp","Cuộc họp"), new(MeetingInvite,"Mời tham gia","Cuộc họp"), new(MeetingRecord,"Ghi hình","Cuộc họp"), new(FileUpload,"Tải file lên","Tài liệu"), new(FileDownload,"Tải file xuống","Tài liệu"), new(AiSummary,"Tóm tắt AI","AI"), new(DashboardView,"Xem dashboard","Báo cáo")
        };
        public static readonly HashSet<string> All = Catalog.Select(x => x.Code).ToHashSet(StringComparer.OrdinalIgnoreCase);
    }
}
