package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;

import java.util.List;
import java.util.Optional;

public interface MeetingTaskShareRepository extends JpaRepository<MeetingTaskShare, String> {

    List<MeetingTaskShare> findByTaskId(String taskId);

    Optional<MeetingTaskShare> findByTaskIdAndUserName(String taskId, String userName);

    boolean existsByTaskIdAndUserName(String taskId, String userName);

    boolean existsByTaskIdAndUserNameAndPermission(String taskId, String userName, int permission);

    void deleteByTaskIdAndUserName(String taskId, String userName);

    void deleteByTaskId(String taskId);
}
