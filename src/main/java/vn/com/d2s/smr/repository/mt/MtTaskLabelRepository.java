package vn.com.d2s.smr.repository.mt;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.mt.MtTaskLabel;

import java.util.List;

public interface MtTaskLabelRepository extends JpaRepository<MtTaskLabel, String> {

    List<MtTaskLabel> findByWorkIdOrderByNameAsc(String workId);

    void deleteByWorkId(String workId);
}
