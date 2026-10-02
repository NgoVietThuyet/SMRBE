package vn.com.d2s.smr.service.mt.impl;

import org.springframework.stereotype.Service;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;
import vn.com.d2s.smr.service.mt.MeetingTranscriptService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MeetingTranscriptServiceImpl implements MeetingTranscriptService {

    private final Map<String, List<MeetingCaptionDto>> meetingTranscripts = new ConcurrentHashMap<>();

    @Override
    public MeetingCaptionDto saveTranscriptSegment(String meetingId, MeetingCaptionDto captionDto) {
        if (meetingId == null || meetingId.isBlank() || captionDto == null) {
            return captionDto;
        }

        if (captionDto.getId() == null || captionDto.getId().isBlank()) {
            captionDto.setId(UUID.randomUUID().toString());
        }
        if (captionDto.getTimestamp() == null) {
            captionDto.setTimestamp(Instant.now());
        }
        captionDto.setMeetingId(meetingId);

        // Chỉ lưu các câu final vào transcript lịch sử
        if (captionDto.isFinal() && captionDto.getText() != null && !captionDto.getText().trim().isEmpty()) {
            List<MeetingCaptionDto> list = meetingTranscripts.computeIfAbsent(meetingId, k -> new CopyOnWriteArrayList<>());
            list.add(captionDto);
            // Giới hạn tối đa 2000 câu để tránh tràn bộ nhớ
            if (list.size() > 2000) {
                list.remove(0);
            }
        }

        return captionDto;
    }

    @Override
    public List<MeetingCaptionDto> getTranscriptsByMeetingId(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return Collections.emptyList();
        }
        List<MeetingCaptionDto> list = meetingTranscripts.get(meetingId);
        if (list == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    @Override
    public void clearTranscripts(String meetingId) {
        if (meetingId != null) {
            meetingTranscripts.remove(meetingId);
        }
    }
}
