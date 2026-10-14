package vn.com.d2s.smr.service.mt;

import vn.com.d2s.smr.dto.mt.room.AttendanceSummaryResponse;
import vn.com.d2s.smr.dto.mt.room.EnterRoomResponse;
import vn.com.d2s.smr.dto.mt.room.LobbyRequestResponse;
import vn.com.d2s.smr.dto.mt.room.PublicMeetingResponse;
import vn.com.d2s.smr.dto.mt.room.RoomParticipantResponse;
import vn.com.d2s.smr.dto.mt.room.SaveWhiteboardRequest;
import vn.com.d2s.smr.dto.mt.room.WhiteboardResponse;

import java.util.List;

/**
 * Phòng họp (P10): vào phòng / phòng chờ, điều khiển thành viên, bảng trắng, ghi hình, tham dự.
 * Quy ước lỗi: IllegalArgumentException → 404/400 · SecurityException → 403 · IllegalStateException → 409.
 */
public interface MeetingRoomService {

    /** Xin vào phòng. Người ngoài (cuộc họp Nội bộ / Công khai) được thêm làm thành viên "Tùy chọn". */
    EnterRoomResponse enter(String userName, String meetingId);

    /** Gửi lại yêu cầu vào phòng sau khi bị từ chối / bị mời ra. */
    EnterRoomResponse requestAgain(String userName, String meetingId);

    List<LobbyRequestResponse> lobby(String userName, String meetingId);

    void decide(String userName, String meetingId, String requestId, boolean admit);

    int admitAll(String userName, String meetingId);

    List<RoomParticipantResponse> participants(String userName, String meetingId);

    void mute(String userName, String meetingId, String target);

    void kick(String userName, String meetingId, String target);

    void changeRole(String userName, String meetingId, String target, int role);

    WhiteboardResponse whiteboard(String userName, String meetingId);

    WhiteboardResponse saveWhiteboard(String userName, String meetingId, SaveWhiteboardRequest request);

    WhiteboardResponse lockWhiteboard(String userName, String meetingId, boolean locked);

    void recording(String userName, String meetingId, boolean on);

    List<AttendanceSummaryResponse> attendance(String userName, String meetingId);

    /** Tên file + nội dung XLSX tham dự. */
    ExportedFile exportAttendance(String userName, String meetingId);

    PublicMeetingResponse publicInfo(String roomCode);

    record ExportedFile(String fileName, byte[] content) {
    }

    /** Lưu bảng trắng bị lệch Revision — kèm bản hiện tại để FE nạp lại. */
    class WhiteboardConflictException extends IllegalStateException {
        private final WhiteboardResponse current;

        public WhiteboardConflictException(WhiteboardResponse current) {
            super("Bảng trắng vừa được người khác cập nhật. Đã tải lại bản mới nhất.");
            this.current = current;
        }

        public WhiteboardResponse current() {
            return current;
        }
    }
}
