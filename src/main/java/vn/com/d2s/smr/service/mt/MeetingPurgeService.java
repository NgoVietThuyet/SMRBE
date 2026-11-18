package vn.com.d2s.smr.service.mt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingTranscriptSegmentRepository;
import vn.com.d2s.smr.repository.mt.MtMeetingAnalysisRepository;
import vn.com.d2s.smr.repository.mt.MtMeetingRecordRepository;
import vn.com.d2s.smr.repository.mt.MtMinutesRepository;
import vn.com.d2s.smr.repository.mt.MtRagChunkRepository;
import vn.com.d2s.smr.repository.mt.MtWhiteboardRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;

import java.util.ArrayList;
import java.util.List;

/**
 * Xóa dữ liệu sinh ra từ một cuộc họp khi cuộc họp bị xóa: biên bản (mọi phiên bản), hồ sơ chốt, kết quả phân tích + chỉ mục RAG,
 * transcript, bảng trắng, file biên bản đã xuất và bản ghi hình (cả file trong kho lưu trữ). Chạy trong giao dịch của người gọi.
 * Lỗi xóa file trong kho chỉ ghi log để không chặn việc xóa cuộc họp.
 */
@Service
public class MeetingPurgeService {

    private static final Logger log = LoggerFactory.getLogger(MeetingPurgeService.class);
    /** CmFile.Type: 2 = bản ghi hình, 6 = mẫu biên bản của cuộc họp, 7 = file biên bản đã xuất. */
    private static final int TYPE_RECORDING = 2;
    private static final int TYPE_MINUTES_TEMPLATE = 6;
    private static final int TYPE_EXPORT = 7;

    private final MtMinutesRepository minutes;
    private final MtMeetingRecordRepository records;
    private final MtMeetingAnalysisRepository analyses;
    private final MtRagChunkRepository chunks;
    private final MeetingTranscriptSegmentRepository segments;
    private final MtWhiteboardRepository whiteboards;
    private final CmFileRepository files;
    private final ObjectStorageService storage;
    private vn.com.d2s.smr.repository.mt.MtPollRepository polls;
    private vn.com.d2s.smr.repository.mt.MtPollVoteRepository pollVotes;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPolls(vn.com.d2s.smr.repository.mt.MtPollRepository polls, vn.com.d2s.smr.repository.mt.MtPollVoteRepository pollVotes) {
        this.polls = polls;
        this.pollVotes = pollVotes;
    }

    public MeetingPurgeService(MtMinutesRepository minutes, MtMeetingRecordRepository records, MtMeetingAnalysisRepository analyses,
                               MtRagChunkRepository chunks, MeetingTranscriptSegmentRepository segments,
                               MtWhiteboardRepository whiteboards, CmFileRepository files, ObjectStorageService storage) {
        this.minutes = minutes;
        this.records = records;
        this.analyses = analyses;
        this.chunks = chunks;
        this.segments = segments;
        this.whiteboards = whiteboards;
        this.files = files;
        this.storage = storage;
    }

    public void purge(MeetingInfo meeting) {
        String id = meeting.getId();
        List<CmFile> doomed = new ArrayList<>();
        if (meeting.getReferenceFileId() != null && !meeting.getReferenceFileId().isBlank()) {
            doomed.addAll(files.findByTypeAndReferenceFileIdOrderByCreateDateDesc(TYPE_RECORDING, meeting.getReferenceFileId()));
        }
        doomed.addAll(files.findByTypeAndReferenceFileIdOrderByCreateDateDesc(TYPE_EXPORT, id));
        doomed.addAll(files.findByTypeAndReferenceFileIdOrderByCreateDateDesc(TYPE_MINUTES_TEMPLATE, id));
        for (CmFile f : doomed) {
            try {
                if (f.getObjectName() != null && !f.getObjectName().isBlank()) storage.delete(f.getObjectName());
            } catch (RuntimeException e) {
                log.warn("Không xóa được file {} trong kho lưu trữ: {}", f.getObjectName(), e.getMessage());
            }
        }
        files.deleteAll(doomed);
        minutes.deleteByMeetingId(id);
        records.deleteByMeetingId(id);
        analyses.deleteByMeetingId(id);
        chunks.deleteByMeetingId(id);
        segments.deleteByMeetingId(id);
        whiteboards.deleteByMeetingId(id);
        if (polls != null && pollVotes != null) {
            List<String> pollIds = polls.findByMeetingIdOrderByCreatedAtAsc(id).stream().map(vn.com.d2s.smr.entity.mt.MtPoll::getId).toList();
            if (!pollIds.isEmpty()) pollVotes.deleteByPollIdIn(pollIds);
            polls.deleteByMeetingId(id);
        }
        log.info("Đã xóa biên bản, bản ghi và dữ liệu liên quan của cuộc họp {} ({} file).", id, doomed.size());
    }
}
