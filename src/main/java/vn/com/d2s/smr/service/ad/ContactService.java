package vn.com.d2s.smr.service.ad;

import vn.com.d2s.smr.dto.ad.contact.ContactResponse;
import vn.com.d2s.smr.dto.common.PagedResultResponse;

/** P15 — Danh bạ: mọi người đã đăng nhập xem được, chỉ đọc. */
public interface ContactService {

    /**
     * @param organizationId lọc theo phòng ban, gồm cả phòng ban con; null = tất cả
     * @param keyword        tìm theo họ tên, tài khoản, email, điện thoại, chức danh — không phân biệt dấu
     */
    PagedResultResponse<ContactResponse> search(String organizationId, String keyword, int page, int pageSize);
}
