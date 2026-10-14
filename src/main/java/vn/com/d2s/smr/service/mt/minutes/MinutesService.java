package vn.com.d2s.smr.service.mt.minutes;

import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.dto.mt.minutes.MinutesDetailResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesJobResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesListItemResponse;
import vn.com.d2s.smr.dto.mt.minutes.MinutesVersionResponse;

/**
 * P13 — Biên bản họp. Quyền: thành viên xem bản đã phát hành; chủ trì (CT) / thư ký (TK) soạn, sinh, gửi duyệt;
 * chỉ chủ trì duyệt / từ chối / phát hành. Bản đã phát hành bất biến.
 * Lỗi: IllegalArgumentException → 404/400 · SecurityException → 403 · IllegalStateException → 409.
 */
public interface MinutesService {

    PagedResultResponse<MinutesListItemResponse> list(String userName, String keyword, int page, int pageSize);

    /** version null = bản mới nhất mà người dùng được xem. */
    MinutesDetailResponse detail(String userName, String meetingId, Integer version);

    /** Sinh bằng AI. Cùng transcript → trả lại job cũ (IdempotencyKey = meetingId + hash transcript). */
    MinutesJobResponse generate(String userName, String meetingId);

    /** Tạo phiên bản nháp mới: chép từ bản mới nhất (nếu có) hoặc trống — để soạn tay / sửa sau khi đã phát hành. */
    MinutesVersionResponse newDraft(String userName, String meetingId);

    MinutesVersionResponse update(String userName, String meetingId, int version, MinutesContent content);

    MinutesVersionResponse submit(String userName, String meetingId, int version);

    MinutesVersionResponse approve(String userName, String meetingId, int version);

    MinutesVersionResponse reject(String userName, String meetingId, int version, String reason);

    /** Phát hành: kiểm tra mọi mục có nguồn, tạo công việc từ việc cần làm đã xác nhận, báo thành viên. */
    MinutesVersionResponse publish(String userName, String meetingId, int version);

    ExportedDocx exportDocx(String userName, String meetingId, int version);

    MeetingCaptionDto updateSegment(String userName, String meetingId, String segmentId, String text, String speakerName);

    record ExportedDocx(String fileName, byte[] content) {
    }
}
