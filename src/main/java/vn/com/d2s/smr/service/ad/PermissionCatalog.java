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
            permission("HR_VIEW", "Xem module nhân sự", "Nhân sự"),
            permission("HR_ORG_CREATE", "Tạo phòng ban", "Cơ cấu tổ chức"),
            permission("HR_ORG_UPDATE", "Cập nhật phòng ban", "Cơ cấu tổ chức"),
            permission("HR_ORG_MOVE", "Di chuyển phòng ban", "Cơ cấu tổ chức"),
            permission("HR_ORG_DELETE", "Xóa phòng ban", "Cơ cấu tổ chức"),
            permission("HR_ORG_PERMISSION", "Phân quyền phòng ban", "Cơ cấu tổ chức"),
            permission("HR_TITLE_VIEW", "Xem chức danh", "Chức danh"),
            permission("HR_TITLE_CREATE", "Tạo chức danh", "Chức danh"),
            permission("HR_TITLE_UPDATE", "Cập nhật chức danh", "Chức danh"),
            permission("HR_TITLE_DELETE", "Xóa chức danh", "Chức danh"),
            permission("HR_TITLE_PERMISSION", "Phân quyền chức danh", "Chức danh"),
            permission("HR_ACCOUNT_VIEW", "Xem tài khoản", "Tài khoản"),
            permission("HR_ACCOUNT_CREATE", "Tạo tài khoản", "Tài khoản"),
            permission("HR_ACCOUNT_UPDATE", "Cập nhật tài khoản", "Tài khoản"),
            permission("HR_ACCOUNT_LOCK", "Khóa/mở tài khoản", "Tài khoản"),
            permission("HR_ACCOUNT_RESET_PASSWORD", "Đặt lại mật khẩu", "Tài khoản"),
            permission("HR_ACCOUNT_TRANSFER", "Điều chuyển phòng ban", "Tài khoản"),
            permission("HR_ACCOUNT_CHANGE_TITLE", "Đổi chức danh", "Tài khoản"),
            permission("HR_ACCOUNT_PERMISSION", "Phân quyền tài khoản", "Tài khoản"),
            permission("MEETING_CREATE", "Tạo cuộc họp", "Cuộc họp"),
            permission("MEETING_INVITE", "Mời tham gia", "Cuộc họp"),
            permission("MEETING_RECORD", "Ghi hình", "Cuộc họp"),
            permission("FILE_UPLOAD", "Tải file lên", "Tài liệu"),
            permission("FILE_DOWNLOAD", "Tải file xuống", "Tài liệu"),
            permission("AI_SUMMARY", "Tóm tắt AI", "AI"),
            permission("DASHBOARD_VIEW", "Xem dashboard", "Báo cáo")
    );

    public static final Set<String> ALL_CODES = DEFINITIONS.stream()
            .map(PermissionDefinition::code)
            .collect(Collectors.toUnmodifiableSet());

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
