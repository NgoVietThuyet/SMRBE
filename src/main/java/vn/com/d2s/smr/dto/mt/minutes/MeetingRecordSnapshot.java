package vn.com.d2s.smr.dto.mt.minutes;

import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ảnh chụp cố định của cuộc họp lúc chốt hồ sơ. Giờ lưu dạng chuỗi ISO (UTC) để JSON không phụ thuộc module thời gian.
 * Sinh biên bản AI và file Word đọc từ đây, không đọc lại dữ liệu sống của cuộc họp.
 */
public record MeetingRecordSnapshot(
        String meetingName,
        String roomCode,
        String expectedStart,
        String expectedEnd,
        String actualStart,
        String actualEnd,
        String content,
        String agenda,
        List<Person> participants,
        List<Segment> segments,
        List<Doc> docs
) {
    public record Person(String userName, String fullName, int type, boolean chairperson) {
    }

    public record Segment(String id, String speakerUserName, String speakerName, String text, String spokenAt) {
    }

    public record Doc(String fileId, String fileName) {
    }

    private static LocalDateTime time(String s) {
        return s == null || s.isBlank() ? null : LocalDateTime.parse(s);
    }

    /** Bản sao của cuộc họp với thông tin như lúc chốt (không lưu DB). */
    public MeetingInfo asMeeting(MeetingInfo live) {
        MeetingInfo m = new MeetingInfo();
        m.setId(live.getId());
        m.setName(meetingName);
        m.setRoomCode(roomCode);
        m.setExpectedStartTime(time(expectedStart));
        m.setExpectedEndTime(time(expectedEnd));
        m.setStartDate(time(actualStart));
        m.setEndDate(time(actualEnd));
        m.setMeetContent(content);
        m.setAgenda(agenda);
        m.setStatus(live.getStatus());
        m.setSettingsJson(live.getSettingsJson());
        m.setReferenceFileId(live.getReferenceFileId());
        return m;
    }

    public List<MeetingPersonal> asPeople(String meetingId) {
        return participants.stream().map(p -> {
            MeetingPersonal x = new MeetingPersonal();
            x.setMeetingId(meetingId);
            x.setUserName(p.userName());
            x.setFullName(p.fullName());
            x.setType(p.type());
            x.setChairperson(p.chairperson());
            return x;
        }).toList();
    }

    public List<MeetingTranscriptSegment> asSegments(String meetingId) {
        return segments.stream().map(s -> {
            MeetingTranscriptSegment x = new MeetingTranscriptSegment();
            x.setId(s.id());
            x.setMeetingId(meetingId);
            x.setSpeakerUserName(s.speakerUserName());
            x.setSpeakerName(s.speakerName());
            x.setText(s.text() == null ? "" : s.text());
            x.setSpokenAt(LocalDateTime.parse(s.spokenAt()));
            return x;
        }).toList();
    }
}
