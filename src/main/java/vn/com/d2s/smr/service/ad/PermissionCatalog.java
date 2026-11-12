package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.permission.PermissionCatalogGroupResponse;
import vn.com.d2s.smr.dto.ad.permission.PermissionDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PermissionCatalog {

    public static final List<PermissionDefinition> DEFINITIONS = List.of(
            permission("ADMIN", "Admin (toàn quyền như quản trị viên)", "Quản trị viên"),
            permission("ADMIN_ORGANIZATIONS", "Cơ cấu tổ chức", "Menu Quản trị"),
            permission("ADMIN_TITLES", "Chức danh", "Menu Quản trị"),
            permission("ADMIN_ACCOUNTS", "Tài khoản", "Menu Quản trị"),
            permission("ADMIN_PERMISSIONS", "Phân quyền", "Menu Quản trị"),
            permission("ADMIN_STATISTICS", "Thống kê", "Menu Quản trị"),
            permission("ADMIN_AUDIT_LOGS", "Nhật ký hoạt động", "Menu Quản trị"),
            permission("ADMIN_SETTINGS", "Cấu hình hệ thống", "Menu Quản trị"),
            permission("MEETING_RECORD", "Ghi hình", "Cuộc họp"),
            permission("FILE_UPLOAD", "Tải file lên", "Tài liệu"),
            permission("FILE_DOWNLOAD", "Tải file xuống", "Tài liệu"),
            permission("AI_SUMMARY", "Tóm tắt AI", "AI")
    );

    public static final Set<String> ALL_CODES = DEFINITIONS.stream()
            .map(PermissionDefinition::code)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Mã đã gỡ: ai cũng tạo được cuộc họp và mời thành viên (người tạo là chủ trì) nên không còn phân quyền.
     * PermissionJson cũ còn chứa các mã này vẫn đọc / lưu được — chúng bị bỏ qua.
     */
    public static final Set<String> RETIRED_CODES = Set.of("MEETING_CREATE", "MEETING_INVITE",
            // Quyền nhân sự HR_* và DASHBOARD_VIEW chưa từng được kiểm tra ở đâu → thay bằng quyền vào từng menu Quản trị (ADMIN_*).
            "HR_VIEW", "HR_ORG_CREATE", "HR_ORG_UPDATE", "HR_ORG_MOVE", "HR_ORG_DELETE", "HR_ORG_PERMISSION",
            "HR_TITLE_VIEW", "HR_TITLE_CREATE", "HR_TITLE_UPDATE", "HR_TITLE_DELETE", "HR_TITLE_PERMISSION",
            "HR_ACCOUNT_VIEW", "HR_ACCOUNT_CREATE", "HR_ACCOUNT_UPDATE", "HR_ACCOUNT_LOCK", "HR_ACCOUNT_RESET_PASSWORD",
            "HR_ACCOUNT_TRANSFER", "HR_ACCOUNT_CHANGE_TITLE", "HR_ACCOUNT_PERMISSION", "DASHBOARD_VIEW");

    private PermissionCatalog() {
    }

    public static List<PermissionCatalogGroupResponse> grouped() {
        Map<String, List<PermissionDefinition>> grouped = DEFINITIONS.stream()
                .collect(Collectors.groupingBy(
                        PermissionDefinition::group,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.entrySet().stream()
                .map(entry -> new PermissionCatalogGroupResponse(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    private static PermissionDefinition permission(String code, String name, String group) {
        return new PermissionDefinition(code, name, group);
    }
}
