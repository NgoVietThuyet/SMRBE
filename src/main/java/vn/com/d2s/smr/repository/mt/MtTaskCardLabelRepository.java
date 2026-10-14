package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtTaskCardLabel;

import java.util.Collection;
import java.util.List;

public interface MtTaskCardLabelRepository extends JpaRepository<MtTaskCardLabel, MtTaskCardLabel.Key> {

    List<MtTaskCardLabel> findByTaskIdIn(Collection<String> taskIds);

    List<MtTaskCardLabel> findByTaskId(String taskId);

    void deleteByTaskId(String taskId);

    void deleteByLabelId(String labelId);
}
