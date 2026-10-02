package vn.com.d2s.smr.repository.cf;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.cf.CmFile;

import java.util.List;

public interface CmFileRepository extends JpaRepository<CmFile, String> {
    List<CmFile> findByReferenceFileIdOrderByCreateDateDesc(String referenceFileId);
}
