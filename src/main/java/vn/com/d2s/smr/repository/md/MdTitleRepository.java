package vn.com.d2s.smr.repository.md;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.md.MdTitle;

import java.util.List;

public interface MdTitleRepository extends JpaRepository<MdTitle, String> {

    List<MdTitle> findAllByOrderByOrderNumberAsc();

    boolean existsByCode(String code);

    boolean existsByCodeAndActiveTrue(String code);
}
