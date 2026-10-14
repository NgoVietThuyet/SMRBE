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

    /** P12: con của nhiều cha (tải cả bảng trong 1 lần). */
    List<MeetingTask> findByParentIdIn(java.util.Collection<String> parentIds);

    /** P12: thứ tự lớn nhất trong một cha (thêm cột / thẻ vào cuối). */
    @org.springframework.data.jpa.repository.Query("select coalesce(max(t.sortOrder), -1) from MeetingTask t where t.parentId = :parentId")
    int maxSortOrder(@org.springframework.data.repository.query.Param("parentId") String parentId);

    /** P12: số việc nhỏ (chưa lưu trữ) trong cả bảng của công việc cấp 0. */
    @org.springframework.data.jpa.repository.Query("select count(c) from MeetingTask c where c.archived = false and c.parentId in (select l.id from MeetingTask l where l.parentId = :rootId)")
    int countCards(@org.springframework.data.repository.query.Param("rootId") String rootId);

    @org.springframework.data.jpa.repository.Query("select count(c) from MeetingTask c where c.archived = false and c.status = 2 and c.parentId in (select l.id from MeetingTask l where l.parentId = :rootId)")
    int countDoneCards(@org.springframework.data.repository.query.Param("rootId") String rootId);
}
