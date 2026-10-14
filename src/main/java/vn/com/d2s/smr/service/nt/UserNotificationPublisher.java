package vn.com.d2s.smr.service.nt;

import java.util.Collection;

/** Đẩy realtime tới MỌI kết nối của đúng những user được chỉ định (không theo phòng họp). */
public interface UserNotificationPublisher {

    void publishToUsers(Collection<String> userNames, String target, Object payload);
}
