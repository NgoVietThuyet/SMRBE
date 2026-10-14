package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtTaskComment;

import java.util.Collection;
import java.util.List;

public interface MtTaskCommentRepository extends JpaRepository<MtTaskComment, String> {

    List<MtTaskComment> findByTaskIdOrderByCreateDateAsc(String taskId);

    List<MtTaskComment> findByTaskIdIn(Collection<String> taskIds);

    void deleteByTaskId(String taskId);
}
