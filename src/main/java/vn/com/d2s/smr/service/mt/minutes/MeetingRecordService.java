package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.mt.minutes.MeetingRecordSnapshot;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.entity.mt.MtMeetingRecord;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTranscriptSegmentRepository;
import vn.com.d2s.smr.repository.mt.MtMeetingRecordRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hồ sơ cuộc họp: chốt (đóng băng) dữ liệu cuộc họp khi họp kết thúc. Chốt lại khi nội dung đổi (sửa transcript,
 * sinh lại biên bản) — nội dung y hệt thì giữ nguyên Revision.
 */
@Service
public class MeetingRecordService {

    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final MeetingTranscriptSegmentRepository segmentRepository;
    private final MtMeetingRecordRepository recordRepository;
    private final CmFileRepository fileRepository;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public MeetingRecordService(MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                                MeetingTranscriptSegmentRepository segmentRepository, MtMeetingRecordRepository recordRepository,
                                CmFileRepository fileRepository, ObjectMapper mapper) {
        this(meetingRepository, personalRepository, segmentRepository, recordRepository, fileRepository, mapper, Clock.systemUTC());
    }

    MeetingRecordService(MeetingInfoRepository meetingRepository, MeetingPersonalRepository personalRepository,
                         MeetingTranscriptSegmentRepository segmentRepository, MtMeetingRecordRepository recordRepository,
                         CmFileRepository fileRepository, ObjectMapper mapper, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.segmentRepository = segmentRepository;
        this.recordRepository = recordRepository;
        this.fileRepository = fileRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Tài liệu cuộc họp (type=1) đọc được văn bản — nguồn bổ sung cho AI cùng transcript. */
    public static List<CmFile> readableDocs(CmFileRepository fileRepository, MeetingInfo meeting) {
        if (fileRepository == null || meeting.getReferenceFileId() == null) return List.of();
        return fileRepository.findByTypeAndReferenceFileIdOrderByCreateDateDesc(1, meeting.getReferenceFileId()).stream()
                .filter(f -> !f.isMinutesFile() && f.getObjectName() != null && MeetingDocumentText.supports(f.getExtension()))
                .limit(10).toList();
    }

    /** Chốt hồ sơ: nội dung đổi so với lần chốt trước thì tăng Revision. Trả ảnh chụp hiện hành. */
    @Transactional
    public MeetingRecordSnapshot seal(String meetingId, String actor) {
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));
        MeetingRecordSnapshot snap = build(meeting);
        String json = write(snap);
        String hash = sha256(json);
        MtMeetingRecord row = recordRepository.findByMeetingId(meetingId).orElse(null);
        if (row != null && hash.equals(row.getSourceHash())) {
            return snap;
        }
        if (row == null) {
            row = new MtMeetingRecord();
            row.setId(UUID.randomUUID().toString().replace("-", ""));
            row.setMeetingId(meetingId);
            row.setRevision(0);
        }
        row.setRevision(row.getRevision() + 1);
        row.setSourceHash(hash);
        row.setSnapshotJson(json);
        row.setSealedBy(actor == null || actor.isBlank() ? "system" : actor);
        row.setSealedAt(LocalDateTime.now(clock));
        recordRepository.save(row);
        return snap;
    }

    @Transactional(readOnly = true)
    public Optional<MeetingRecordSnapshot> find(String meetingId) {
        return recordRepository.findByMeetingId(meetingId).map(this::read);
    }

    /** Dấu vết bản chốt (id + revision + hash) — thay đổi khi chốt lại, dùng để làm mới file Word. */
    @Transactional(readOnly = true)
    public Optional<String> fingerprint(String meetingId) {
        return recordRepository.findByMeetingId(meetingId).map(r -> r.getRevision() + ":" + r.getSourceHash());
    }

    private MeetingRecordSnapshot build(MeetingInfo m) {
        List<MeetingPersonal> people = personalRepository.findByMeetingId(m.getId());
        List<MeetingTranscriptSegment> segs = segmentRepository.findByMeetingIdOrderBySpokenAtAsc(m.getId());
        List<CmFile> docs = readableDocs(fileRepository, m);
        return new MeetingRecordSnapshot(
                m.getName(), m.getRoomCode(),
                str(m.getExpectedStartTime()), str(m.getExpectedEndTime()), str(m.getStartDate()), str(m.getEndDate()),
                m.getMeetContent(), m.getAgenda(),
                people.stream().sorted(Comparator.comparing(MeetingPersonal::getUserName, String.CASE_INSENSITIVE_ORDER))
                        .map(p -> new MeetingRecordSnapshot.Person(p.getUserName(), p.getFullName(), p.getType(), p.isChairperson())).toList(),
                segs.stream().map(s -> new MeetingRecordSnapshot.Segment(s.getId(), s.getSpeakerUserName(), s.getSpeakerName(),
                        s.getText(), str(s.getSpokenAt()))).toList(),
                docs.stream().sorted(Comparator.comparing(CmFile::getId))
                        .map(f -> new MeetingRecordSnapshot.Doc(f.getId(), f.getFileName())).toList());
    }

    private static String str(LocalDateTime t) {
        return t == null ? null : t.toString();
    }

    private String write(MeetingRecordSnapshot s) {
        try {
            return mapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được hồ sơ cuộc họp.", e);
        }
    }

    private MeetingRecordSnapshot read(MtMeetingRecord r) {
        try {
            return mapper.readValue(r.getSnapshotJson(), MeetingRecordSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Hồ sơ cuộc họp bị lỗi dữ liệu.", e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
