package vn.com.d2s.smr.repository.ad;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.ad.AdFaceTemplate;

import java.util.Collection;
import java.util.List;

public interface AdFaceTemplateRepository extends JpaRepository<AdFaceTemplate, String> {

    List<AdFaceTemplate> findByUserNameIgnoreCaseAndActiveTrue(String userName);

    List<AdFaceTemplate> findByUserNameInAndActiveTrue(Collection<String> userNames);

    List<AdFaceTemplate> findByActiveTrue();

    boolean existsByUserNameIgnoreCaseAndActiveTrue(String userName);

    void deleteByUserNameIgnoreCase(String userName);
}
