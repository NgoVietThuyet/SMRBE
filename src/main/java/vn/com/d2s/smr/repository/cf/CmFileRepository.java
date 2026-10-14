package vn.com.d2s.smr.repository.cf;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.d2s.smr.entity.cf.CmFile;

import java.util.List;

public interface CmFileRepository extends JpaRepository<CmFile, String> {
    List<CmFile> findByReferenceFileIdOrderByCreateDateDesc(String referenceFileId);

    List<CmFile> findByTypeOrderByCreateDateDesc(int type);

    /** P12: file đính kèm của nhiều việc (Type = 3, RefrenceFileId = Id việc). */
    List<CmFile> findByTypeAndReferenceFileIdIn(int type, java.util.Collection<String> referenceFileIds);

    List<CmFile> findByTypeAndReferenceFileIdOrderByCreateDateDesc(int type, String referenceFileId);
}
