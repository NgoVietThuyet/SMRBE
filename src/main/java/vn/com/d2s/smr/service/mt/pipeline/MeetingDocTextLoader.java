package vn.com.d2s.smr.service.mt.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.minutes.MeetingDocumentText;
import vn.com.d2s.smr.service.mt.minutes.MeetingRecordService;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Đọc văn bản các tài liệu của cuộc họp (có nhớ tạm theo Id tệp) để dựng chỉ mục hỏi đáp khi cuộc họp chưa chạy pipeline. */
@Component
public class MeetingDocTextLoader {

    private static final Logger log = LoggerFactory.getLogger(MeetingDocTextLoader.class);
    private static final int CACHE_MAX = 60;

    private final CmFileRepository fileRepository;
    private final ObjectStorageService storage;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public MeetingDocTextLoader(CmFileRepository fileRepository, ObjectStorageService storage) {
        this.fileRepository = fileRepository;
        this.storage = storage;
    }

    public List<MinutesAiGenerator.Doc> load(MeetingInfo meeting) {
        List<MinutesAiGenerator.Doc> docs = new ArrayList<>();
        for (CmFile f : MeetingRecordService.readableDocs(fileRepository, meeting)) {
            String text = cache.get(f.getId());
            if (text == null) {
                try (InputStream in = storage.download(f.getObjectName())) {
                    text = MeetingDocumentText.extract(in, f.getExtension(), f.getFileSize() == null ? 0 : f.getFileSize().longValue());
                } catch (Exception e) {
                    log.warn("Không đọc được tài liệu {} cho hỏi đáp: {}", f.getId(), e.getMessage());
                }
                if (text != null) {
                    if (cache.size() >= CACHE_MAX) cache.clear();
                    cache.put(f.getId(), text);
                }
            }
            if (text != null) docs.add(new MinutesAiGenerator.Doc(f.getFileName(), text));
        }
        return docs;
    }
}
