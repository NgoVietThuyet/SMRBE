package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtTaskChecklistItem;

import java.util.Collection;
import java.util.List;

public interface MtTaskChecklistItemRepository extends JpaRepository<MtTaskChecklistItem, String> {

    List<MtTaskChecklistItem> findByTaskIdOrderBySortOrderAsc(String taskId);

    List<MtTaskChecklistItem> findByTaskIdIn(Collection<String> taskIds);

    void deleteByTaskId(String taskId);
}
