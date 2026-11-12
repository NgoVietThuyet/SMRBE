package vn.com.d2s.smr.seed;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quy tắc tài khoản demo, thuần tính toán (không đụng DB) → có unit test.
 * Email = tên đăng nhập + "@gmail.com"; mật khẩu = tên đăng nhập + "123".
 */
public final class DemoAccounts {

    /** Gốc hệ thống (tài khoản Admin), không phải phòng ban nghiệp vụ. */
    static final String SYSTEM_ORG = "ORG_SYS";
    /** Chức danh của Admin, không gán cho tài khoản demo. */
    static final String ADMIN_TITLE = "ADMIN";

    private DemoAccounts() {
    }

    public static String email(String userName) {
        return userName + "@gmail.com";
    }

    public static String password(String userName) {
        return userName + "123";
    }

    /** Bỏ dấu (kể cả đ), viết thường, gộp khoảng trắng → so khớp họ tên không phụ thuộc dấu. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public record OrgRow(String id, String parentId) {
    }

    /** Phòng ban cấp lá (không có phòng con), bỏ gốc hệ thống; thứ tự theo Id để kết quả ổn định. */
    public static List<String> leafOrgIds(List<OrgRow> activeOrgs) {
        Set<String> parents = activeOrgs.stream().map(OrgRow::parentId).filter(p -> p != null && !p.isBlank()).collect(Collectors.toSet());
        return activeOrgs.stream().map(OrgRow::id)
                .filter(id -> !parents.contains(id) && !SYSTEM_ORG.equalsIgnoreCase(id))
                .sorted().toList();
    }

    /** Mã chức danh dùng được cho tài khoản demo: đang hoạt động, trừ Admin; thứ tự theo mã. */
    public static List<String> usableTitleCodes(List<String> activeTitleCodes) {
        return activeTitleCodes.stream().filter(c -> !ADMIN_TITLE.equalsIgnoreCase(c)).sorted().toList();
    }

    /** Mỗi tài khoản một phần tử trong {@code candidates}, ngẫu nhiên nhưng cố định theo (muối, tên đăng nhập). */
    public static Map<String, String> assign(List<String> userNames, List<String> candidates, String salt) {
        Map<String, String> out = new LinkedHashMap<>();
        if (candidates.isEmpty()) return out;
        for (String u : userNames) {
            out.put(u, candidates.get(new Random((salt + ":" + u).hashCode()).nextInt(candidates.size())));
        }
        return out;
    }

    public record HostResolution(Map<String, String> aliasToUser, List<String> problems) {
    }

    /**
     * Tìm tên đăng nhập thật của từng chủ trì theo họ tên (so khớp không phân biệt dấu / hoa thường).
     * {@code userToFullName}: các tài khoản đang hoạt động. Không thấy, trùng nhiều người, hoặc một người
     * được gán cho hai tên giữ chỗ → báo vấn đề, không đoán.
     */
    public static HostResolution resolveHosts(List<DemoData.HostAccount> wanted, Map<String, String> userToFullName) {
        Map<String, String> aliasToUser = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (DemoData.HostAccount h : wanted) {
            String target = normalize(h.fullName());
            List<String> found = userToFullName.entrySet().stream()
                    .filter(e -> normalize(e.getValue()).equals(target)).map(Map.Entry::getKey).toList();
            if (found.isEmpty()) {
                problems.add("Không tìm thấy tài khoản đang hoạt động có họ tên '" + h.fullName() + "' (chủ trì " + h.alias() + ").");
            } else if (found.size() > 1) {
                problems.add("Có " + found.size() + " tài khoản trùng họ tên '" + h.fullName() + "': " + String.join(", ", found)
                        + ". Cần chọn đúng một tài khoản.");
            } else if (aliasToUser.containsValue(found.get(0))) {
                problems.add("Tài khoản " + found.get(0) + " bị gán cho hai chủ trì.");
            } else {
                aliasToUser.put(h.alias(), found.get(0));
            }
        }
        return new HostResolution(aliasToUser, problems);
    }
}
