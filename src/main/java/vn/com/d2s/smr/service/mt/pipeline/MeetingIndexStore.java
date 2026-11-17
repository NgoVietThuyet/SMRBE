package vn.com.d2s.smr.service.mt.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.entity.mt.MtMeetingAnalysis;
import vn.com.d2s.smr.entity.mt.MtRagChunk;
import vn.com.d2s.smr.repository.mt.MtMeetingAnalysisRepository;
import vn.com.d2s.smr.repository.mt.MtRagChunkRepository;

import java.util.List;
import java.util.Optional;

/** Lưu bền kết quả pipeline (phân tích + đoạn RAG). Tách bean riêng để các thao tác xóa-rồi-ghi chạy trong một giao dịch. */
@Component
public class MeetingIndexStore {

    private final MtMeetingAnalysisRepository analysisRepository;
    private final MtRagChunkRepository chunkRepository;

    public MeetingIndexStore(MtMeetingAnalysisRepository analysisRepository, MtRagChunkRepository chunkRepository) {
        this.analysisRepository = analysisRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public void saveAnalysis(MtMeetingAnalysis a) {
        analysisRepository.findByMeetingId(a.getMeetingId()).ifPresent(old -> {
            a.setId(old.getId());
            a.setCreatedAt(old.getCreatedAt());
        });
        analysisRepository.save(a);
    }

    @Transactional(readOnly = true)
    public Optional<MtMeetingAnalysis> analysis(String meetingId) {
        return analysisRepository.findByMeetingId(meetingId);
    }

    @Transactional
    public void replaceChunks(String meetingId, List<MtRagChunk> chunks) {
        chunkRepository.deleteByMeetingId(meetingId);
        chunkRepository.saveAll(chunks);
    }

    @Transactional(readOnly = true)
    public List<MtRagChunk> chunks(String meetingId) {
        return chunkRepository.findByMeetingIdOrderByOrd(meetingId);
    }
}
