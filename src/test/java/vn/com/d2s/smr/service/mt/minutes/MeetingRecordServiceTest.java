package vn.com.d2s.smr.service.mt.minutes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.com.d2s.smr.dto.mt.minutes.MeetingRecordSnapshot;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.entity.mt.MtMeetingRecord;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTranscriptSegmentRepository;
import vn.com.d2s.smr.repository.mt.MtMeetingRecordRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MeetingRecordServiceTest {

    private static final String MID = "m1";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 15, 3, 0);

    private final AtomicReference<MtMeetingRecord> row = new AtomicReference<>();
    private final List<MeetingTranscriptSegment> segments = new ArrayList<>();
    private MeetingInfo meeting;
    private MeetingRecordService service;

    @BeforeEach
    void setUp() {
        MeetingInfoRepository meetings = Mockito.mock(MeetingInfoRepository.class);
        MeetingPersonalRepository people = Mockito.mock(MeetingPersonalRepository.class);
        MeetingTranscriptSegmentRepository segs = Mockito.mock(MeetingTranscriptSegmentRepository.class);
        MtMeetingRecordRepository records = Mockito.mock(MtMeetingRecordRepository.class);
        CmFileRepository files = Mockito.mock(CmFileRepository.class);

        meeting = new MeetingInfo();
        meeting.setId(MID);
        meeting.setName("Họp giao ban");
        meeting.setRoomCode("ABC123");
        meeting.setStatus(3);
        meeting.setStartDate(T0);
        meeting.setEndDate(T0.plusHours(1));
        when(meetings.findByIdAndDeletedFalse(MID)).thenAnswer(i -> Optional.of(meeting));

        MeetingPersonal host = new MeetingPersonal();
        host.setUserName("host");
        host.setFullName("Chủ Trì");
        host.setType(1);
        host.setChairperson(true);
        when(people.findByMeetingId(MID)).thenReturn(List.of(host));

        MeetingTranscriptSegment s = new MeetingTranscriptSegment();
        s.setId("s1");
        s.setMeetingId(MID);
        s.setSpeakerName("Chủ Trì");
        s.setText("Thống nhất ra mắt vào thứ Hai.");
        s.setSpokenAt(T0.plusMinutes(5));
        segments.add(s);
        when(segs.findByMeetingIdOrderBySpokenAtAsc(MID)).thenAnswer(i -> List.copyOf(segments));

        when(records.findByMeetingId(MID)).thenAnswer(i -> Optional.ofNullable(row.get()));
        when(records.save(any(MtMeetingRecord.class))).thenAnswer(i -> {
            row.set(i.getArgument(0));
            return i.getArgument(0);
        });

        service = new MeetingRecordService(meetings, people, segs, records, files, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-15T05:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sealFreezesMeetingDataAndKeepsRevisionWhenNothingChanged() {
        MeetingRecordSnapshot snap = service.seal(MID, "system");

        assertThat(row.get().getRevision()).isEqualTo(1);
        assertThat(row.get().getSealedBy()).isEqualTo("system");
        assertThat(snap.meetingName()).isEqualTo("Họp giao ban");
        assertThat(snap.participants()).extracting(MeetingRecordSnapshot.Person::userName).containsExactly("host");
        assertThat(snap.segments()).hasSize(1);

        service.seal(MID, "host");
        assertThat(row.get().getRevision()).isEqualTo(1);
        assertThat(row.get().getSealedBy()).isEqualTo("system");
    }

    @Test
    void recordKeepsOldFactsAfterMeetingIsRenamedAndResealsWhenTranscriptEdited() {
        service.seal(MID, "system");
        meeting.setName("Tên mới sau khi họp");

        MeetingRecordSnapshot frozen = service.find(MID).orElseThrow();
        assertThat(frozen.meetingName()).isEqualTo("Họp giao ban");
        assertThat(frozen.asMeeting(meeting).getName()).isEqualTo("Họp giao ban");

        segments.get(0).setText("Thống nhất ra mắt vào thứ Ba.");
        service.seal(MID, "host");

        assertThat(row.get().getRevision()).isEqualTo(2);
        assertThat(row.get().getSealedBy()).isEqualTo("host");
        // Chốt lại lấy luôn tên hiện tại và transcript đã sửa.
        MeetingRecordSnapshot resealed = service.find(MID).orElseThrow();
        assertThat(resealed.meetingName()).isEqualTo("Tên mới sau khi họp");
        assertThat(resealed.segments().get(0).text()).contains("thứ Ba");
        assertThat(service.fingerprint(MID)).contains("2:" + row.get().getSourceHash());
    }
}
