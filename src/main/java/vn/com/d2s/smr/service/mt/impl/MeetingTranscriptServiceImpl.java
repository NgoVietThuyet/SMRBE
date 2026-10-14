package vn.com.d2s.smr.service.mt.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.repository.mt.MeetingTranscriptSegmentRepository;
import vn.com.d2s.smr.service.mt.MeetingTranscriptService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transcript cuộc họp. P13: lưu bền vào MeetingTranscriptSegments (khởi động lại BE không mất).
 * Constructor không tham số (dùng trong unit test cũ) giữ chế độ lưu RAM.
 */
@Service
public class MeetingTranscriptServiceImpl implements MeetingTranscriptService {

    private final MeetingTranscriptSegmentRepository repository;
    private final Map<String, List<MeetingCaptionDto>> memory = new ConcurrentHashMap<>();

    @Autowired
    public MeetingTranscriptServiceImpl(MeetingTranscriptSegmentRepository repository) {
        this.repository = repository;
    }

    public MeetingTranscriptServiceImpl() {
        this.repository = null;
    }

    @Override
    @Transactional
    public MeetingCaptionDto saveTranscriptSegment(String meetingId, MeetingCaptionDto captionDto) {
        if (meetingId == null || meetingId.isBlank() || captionDto == null) {
            return captionDto;
        }
        if (captionDto.getId() == null || captionDto.getId().isBlank() || captionDto.getId().length() > 100) {
            captionDto.setId(UUID.randomUUID().toString());
        }
        if (captionDto.getTimestamp() == null) {
            captionDto.setTimestamp(Instant.now());
        }
        captionDto.setMeetingId(meetingId);
        // Chỉ lưu câu cuối (final) có nội dung.
        if (!captionDto.isFinal() || captionDto.getText() == null || captionDto.getText().isBlank()) {
            return captionDto;
        }
        if (repository == null) {
            List<MeetingCaptionDto> list = memory.computeIfAbsent(meetingId, k -> new CopyOnWriteArrayList<>());
            list.add(captionDto);
            if (list.size() > 2000) list.remove(0);
            return captionDto;
        }
        // Cùng Id (FE gửi lại đoạn) → ghi đè, không nhân bản.
        MeetingTranscriptSegment s = repository.findById(captionDto.getId()).orElseGet(MeetingTranscriptSegment::new);
        s.setId(captionDto.getId());
        s.setMeetingId(meetingId);
        s.setSpeakerUserName(captionDto.getSpeakerUserName());
        s.setSpeakerName(truncate(captionDto.getSpeakerName(), 200));
        s.setText(captionDto.getText().trim());
        s.setLanguage(truncate(captionDto.getLanguage(), 10));
        s.setSpokenAt(LocalDateTime.ofInstant(captionDto.getTimestamp(), ZoneOffset.UTC));
        repository.save(s);
        return captionDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingCaptionDto> getTranscriptsByMeetingId(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return Collections.emptyList();
        }
        if (repository == null) {
            List<MeetingCaptionDto> list = memory.get(meetingId);
            return list == null ? Collections.emptyList() : new ArrayList<>(list);
        }
        return repository.findByMeetingIdOrderBySpokenAtAsc(meetingId).stream().map(MeetingTranscriptServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public void clearTranscripts(String meetingId) {
        if (meetingId == null) return;
        if (repository == null) {
            memory.remove(meetingId);
        } else {
            repository.deleteByMeetingId(meetingId);
        }
    }

    public static MeetingCaptionDto toDto(MeetingTranscriptSegment s) {
        MeetingCaptionDto d = new MeetingCaptionDto();
        d.setId(s.getId());
        d.setMeetingId(s.getMeetingId());
        d.setSpeakerId(s.getSpeakerUserName());
        d.setSpeakerUserName(s.getSpeakerUserName());
        d.setSpeakerName(s.getSpeakerName());
        d.setText(s.getText());
        d.setLanguage(s.getLanguage());
        d.setFinal(true);
        d.setTimestamp(s.getSpokenAt().toInstant(ZoneOffset.UTC));
        return d;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
