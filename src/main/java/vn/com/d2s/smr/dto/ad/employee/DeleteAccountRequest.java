package vn.com.d2s.smr.dto.ad.employee;

import java.util.List;

/** Danh sách cuộc họp được chuyển chủ trì; cuộc họp chủ trì chưa kết thúc mà không có trong danh sách sẽ bị xóa. */
public record DeleteAccountRequest(List<Transfer> transfers) {
    public record Transfer(String meetingId, String newOwner) {
    }

    public List<Transfer> safeTransfers() {
        return transfers == null ? List.of() : transfers;
    }
}
