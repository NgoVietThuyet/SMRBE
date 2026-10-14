package vn.com.d2s.smr.dto.ad.contact;

/** Một người trong Danh bạ (P15) — chỉ thông tin liên hệ, không có quyền / trạng thái tài khoản. */
public record ContactResponse(
        String userName,
        String fullName,
        String email,
        String phone,
        String orgId,
        String organizationName,
        String titleCode,
        String titleName
) {
}
