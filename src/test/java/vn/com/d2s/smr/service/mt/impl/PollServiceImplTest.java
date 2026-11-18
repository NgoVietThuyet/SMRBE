package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.dto.mt.poll.CreatePollRequest;
import vn.com.d2s.smr.dto.mt.poll.PollResponse;
import vn.com.d2s.smr.dto.mt.poll.PollSummary;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MtPoll;
import vn.com.d2s.smr.entity.mt.MtPollVote;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MtPollRepository;
import vn.com.d2s.smr.repository.mt.MtPollVoteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Kho trong bộ nhớ (mock có trạng thái) để kiểm tra luật biểu quyết. */
class PollServiceImplTest {

    static final String MID = "m1";
    final List<MtPoll> pollRows = new ArrayList<>();
    final List<MtPollVote> voteRows = new ArrayList<>();
    final MeetingInfo meeting = new MeetingInfo();
    PollServiceImpl service;

    static MeetingPersonal person(String user, String name, int type) {
        MeetingPersonal p = new MeetingPersonal();
        p.setMeetingId(MID);
        p.setUserName(user);
        p.setFullName(name);
        p.setType(type);
        return p;
    }

    @BeforeEach
    void setUp() {
        meeting.setId(MID);
        meeting.setStatus(2);
        MtPollRepository polls = mock(MtPollRepository.class);
        MtPollVoteRepository votes = mock(MtPollVoteRepository.class);
        MeetingInfoRepository meetings = mock(MeetingInfoRepository.class);
        MeetingPersonalRepository personals = mock(MeetingPersonalRepository.class);
        List<MeetingPersonal> people = List.of(person("chair", "Chủ Trì", 1), person("sec", "Thư Ký", 3),
                person("mem1", "Thành Viên 1", 4), person("mem2", "Thành Viên 2", 4), person("guest_x", "Khách", 5));
        when(meetings.findByIdAndDeletedFalse(MID)).thenReturn(Optional.of(meeting));
        when(personals.findByMeetingId(MID)).thenReturn(people);
        when(personals.findByMeetingIdAndUserName(anyString(), anyString())).thenAnswer(i ->
                people.stream().filter(p -> p.getUserName().equals(i.getArgument(1))).findFirst());
        when(polls.save(any(MtPoll.class))).thenAnswer(i -> {
            MtPoll p = i.getArgument(0);
            if (!pollRows.contains(p)) pollRows.add(p);
            return p;
        });
        when(polls.findById(anyString())).thenAnswer(i -> pollRows.stream().filter(p -> p.getId().equals(i.getArgument(0))).findFirst());
        when(polls.findByMeetingIdOrderByCreatedAtAsc(MID)).thenAnswer(i -> new ArrayList<>(pollRows));
        when(polls.findByMeetingIdAndStatus(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i ->
                pollRows.stream().filter(p -> p.getStatus() == (int) i.getArgument(1)).toList());
        when(votes.save(any(MtPollVote.class))).thenAnswer(i -> {
            MtPollVote v = i.getArgument(0);
            if (!voteRows.contains(v)) voteRows.add(v);
            return v;
        });
        when(votes.findByPollIdAndUserName(anyString(), anyString())).thenAnswer(i -> voteRows.stream()
                .filter(v -> v.getPollId().equals(i.getArgument(0)) && v.getUserName().equals(i.getArgument(1))).findFirst());
        when(votes.findByPollIdIn(anyCollection())).thenAnswer(i -> voteRows.stream().filter(v -> ((java.util.Collection<?>) i.getArgument(0)).contains(v.getPollId())).toList());
        service = new PollServiceImpl(polls, votes, meetings, personals, new ObjectMapper());
    }

    private PollResponse newPoll(boolean anonymous) {
        return service.create("chair", MID, new CreatePollRequest("Chọn cách nào?", List.of("Cách 1", "Cách 2", "Trắng"), anonymous));
    }

    @Test
    void onlyChairCoChairAndSecretaryCanCreateAndMeetingMustBeOngoing() {
        assertThatThrownBy(() -> service.create("mem1", MID, new CreatePollRequest("Q", List.of("A", "B"), false))).isInstanceOf(SecurityException.class);
        assertThat(service.create("sec", MID, new CreatePollRequest("Q", List.of("A", "B"), false)).id()).isNotBlank();
        meeting.setStatus(3);
        assertThatThrownBy(this::publicPoll).isInstanceOf(IllegalStateException.class);
    }

    private PollResponse publicPoll() {
        return newPoll(false);
    }

    @Test
    void validatesOptions() {
        assertThatThrownBy(() -> service.create("chair", MID, new CreatePollRequest("Q", List.of("A", "A", " "), false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("chair", MID, new CreatePollRequest(" ", List.of("A", "B"), false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void membersVoteOncePerPollAndCanChangeVoteGuestsCannot() {
        PollResponse p = newPoll(false);
        service.vote("mem1", MID, p.id(), 0);
        service.vote("mem2", MID, p.id(), 1);
        PollResponse after = service.vote("mem1", MID, p.id(), 1);   // đổi phiếu, không nhân đôi

        assertThat(after.counts()).containsExactly(0, 2, 0);
        assertThat(after.total()).isEqualTo(2);
        assertThat(after.myVote()).isEqualTo(1);
        assertThat(after.winner()).isEqualTo(1);
        assertThatThrownBy(() -> service.vote("guest_x", MID, p.id(), 0)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.vote("mem1", MID, p.id(), 9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closedPollRejectsVotesAndOnlyManagersClose() {
        PollResponse p = newPoll(false);
        assertThatThrownBy(() -> service.close("mem1", MID, p.id())).isInstanceOf(SecurityException.class);
        PollResponse closed = service.close("sec", MID, p.id());
        assertThat(closed.closed()).isTrue();
        assertThatThrownBy(() -> service.vote("mem1", MID, p.id(), 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anonymousPollNeverExposesVotersPublicPollDoes() {
        PollResponse anon = newPoll(true);
        PollResponse open = newPoll(false);
        service.vote("mem1", MID, anon.id(), 0);
        service.vote("mem1", MID, open.id(), 0);

        List<PollResponse> list = service.list("mem2", MID);
        assertThat(list.get(0).voters()).isNull();
        assertThat(list.get(1).voters().get(0)).containsExactly("Thành Viên 1");
    }

    @Test
    void summariesHaveCountsOnlyAndTieHasNoWinner() {
        PollResponse p = newPoll(false);
        service.vote("mem1", MID, p.id(), 0);
        service.vote("mem2", MID, p.id(), 1);
        service.closeAll(MID);

        List<PollSummary> sums = service.summaries(MID);
        assertThat(sums).hasSize(1);
        assertThat(sums.get(0).closed()).isTrue();
        assertThat(sums.get(0).counts()).containsExactly(1, 1, 0);
        assertThat(sums.get(0).winner()).isEqualTo(-1);
        String text = sums.get(0).toPromptText();
        assertThat(text).contains("Hòa phiếu").doesNotContain("mem1").doesNotContain("Thành Viên");
    }
}
