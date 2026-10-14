package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSettingsDto;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MtLobbyRequest;
import vn.com.d2s.smr.repository.mt.MtLobbyRequestRepository;

import java.util.Locale;

/**
 * Quy tắc vào phòng họp (P10) — dùng chung cho REST {@code room/enter} và hub {@code JoinMeeting}.
 * Chỉ đọc dữ liệu, không ghi.
 */
@Component
public class RoomAdmission {

    /** Trạng thái vào phòng của một thành viên. */
    public enum State {
        /** Được vào phòng. */
        ADMITTED,
        /** Đang chờ chủ trì duyệt. */
        LOBBY,
        /** Bị từ chối / bị mời ra — phải xin vào lại. */
        REJECTED,
        /** Phòng chờ đang bật nhưng chưa gửi yêu cầu. */
        NEED_REQUEST,
        /** Cuộc họp chưa bắt đầu và không cho vào trước chủ trì. */
        WAIT_HOST,
        /** Bản nháp — chưa phát hành. */
        NOT_READY,
        ENDED,
        CANCELLED,
    }

    private final MtLobbyRequestRepository lobbyRepository;
    private final ObjectMapper objectMapper;

    public RoomAdmission(MtLobbyRequestRepository lobbyRepository, ObjectMapper objectMapper) {
        this.lobbyRepository = lobbyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Người quản lý cuộc họp: chủ trì (người tạo, Type 1) và đồng chủ trì được trao quyền (Type 2 + IsChuTri).
     * Sửa / hủy / bắt đầu cuộc họp, thêm bớt thành viên, điều khiển phòng, duyệt biên bản.
     * Chỉ xét cờ IsChuTri chứ không xét Type 2: dữ liệu cũ có dòng Type 2 mặc định, chưa từng được trao quyền.
     */
    public static boolean isModerator(MeetingPersonal p) {
        return p != null && (p.isChairperson() || p.getType() == 1);
    }

    /** Chủ trì = người tạo cuộc họp: không bị xóa, mời ra hay đổi vai trò. */
    public static boolean isOwner(MeetingPersonal p) {
        return p != null && p.getType() == 1;
    }

    /** Vai trò lưu cho người được mời: "Chủ trì" (1) dành cho người tạo → người được trao quyền là Đồng chủ trì (2). */
    public static int invitedRole(int requested) {
        if (requested <= 0 || requested > 5) return 4;
        return requested == 1 ? 2 : requested;
    }

    /** Khách vào bằng link (Auth/GuestSession tạo userName "guest_xxx"). */
    public static boolean isGuest(String userName) {
        return userName != null && userName.toLowerCase(Locale.ROOT).startsWith("guest_");
    }

    public MeetingSettingsDto settings(MeetingInfo meeting) {
        return MeetingSettingsDto.fromJson(meeting.getSettingsJson(), objectMapper);
    }

    public State evaluate(MeetingInfo meeting, MeetingPersonal personal) {
        switch (meeting.getStatus()) {
            case 4:
                return State.CANCELLED;
            case 3:
            case 5:
                return State.ENDED;
            case 0:
                return State.NOT_READY;
            default:
                break;
        }
        if (isModerator(personal)) {
            return State.ADMITTED;
        }
        MeetingSettingsDto settings = settings(meeting);
        if (meeting.getStatus() == 1 && !settings.allowJoinBeforeHost()) {
            return State.WAIT_HOST;
        }
        MtLobbyRequest latest = lobbyRepository
                .findFirstByMeetingIdAndUserNameOrderByRequestedAtDesc(meeting.getId(), personal.getUserName())
                .orElse(null);
        if (latest != null) {
            switch (latest.getStatus()) {
                case MtLobbyRequest.PENDING:
                    return State.LOBBY;
                case MtLobbyRequest.ADMITTED:
                    return State.ADMITTED;
                case MtLobbyRequest.REJECTED:
                    return State.REJECTED;
                default:
                    break;
            }
        }
        return settings.lobbyEnabled() ? State.NEED_REQUEST : State.ADMITTED;
    }
}
