package vn.com.d2s.smr.service.mt;

import java.util.Collection;
import java.util.Set;

/** Đẩy sự kiện realtime của phòng họp (P10) qua /meetinghub. Bản mặc định không làm gì để unit test không cần hub. */
public interface RoomEventPublisher {

    /** Gửi cho mọi kết nối đang ở trong phòng {@code meetingId}. */
    void broadcastToRoom(String meetingId, String target, Object payload);

    /** Gửi cho mọi kết nối của các user (kể cả khi chưa vào phòng — dùng cho phòng chờ, bị mời ra). */
    void sendToUsers(Collection<String> userNames, String target, Object payload);

    /** userName (viết thường) đang có kết nối trong phòng. */
    Set<String> onlineUsers(String meetingId);

    /** Đẩy user ra khỏi nhóm realtime của phòng (bị mời ra). */
    void removeUserFromRoom(String meetingId, String userName);

    /** Cập nhật trạng thái khóa bảng trắng để hub chặn nét vẽ của người không phải chủ trì. */
    void setWhiteboardLocked(String meetingId, boolean locked);

    RoomEventPublisher NO_OP = new RoomEventPublisher() {
        @Override public void broadcastToRoom(String meetingId, String target, Object payload) { }
        @Override public void sendToUsers(Collection<String> userNames, String target, Object payload) { }
        @Override public Set<String> onlineUsers(String meetingId) { return Set.of(); }
        @Override public void removeUserFromRoom(String meetingId, String userName) { }
        @Override public void setWhiteboardLocked(String meetingId, boolean locked) { }
    };
}
