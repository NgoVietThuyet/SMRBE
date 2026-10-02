package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.com.d2s.smr.entity.mt.MeetingTask;

import java.util.List;

public interface MeetingTaskRepository extends JpaRepository<MeetingTask, String>, JpaSpecificationExecutor<MeetingTask> {

    List<MeetingTask> findByMeetingId(String meetingId);

    List<MeetingTask> findByParentId(String parentId);

    boolean existsByParentId(String parentId);

    int countByParentId(String parentId);

    int countByParentIdAndStatus(String parentId, int status);
}
