package vn.com.d2s.smr.service.mt;

import vn.com.d2s.smr.dto.mt.meeting.MeetingCaptionDto;

import java.util.List;

public interface MeetingTranscriptService {
    MeetingCaptionDto saveTranscriptSegment(String meetingId, MeetingCaptionDto captionDto);
    List<MeetingCaptionDto> getTranscriptsByMeetingId(String meetingId);
    void clearTranscripts(String meetingId);
}
