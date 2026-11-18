package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.config.AdminAccess;
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
import vn.com.d2s.smr.service.mt.PollService;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Biểu quyết: chủ trì / đồng chủ trì / thư ký tạo và đóng; thành viên có tài khoản (không khách) bỏ phiếu, được đổi phiếu đến khi đóng.
 * Biểu quyết ẩn danh không bao giờ trả tên người bỏ phiếu; AI chỉ nhận số phiếu tổng ({@link PollSummary}).
 */
@Service
public class PollServiceImpl implements PollService {

    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 8;
    static final int MAX_OPTION_CHARS = 120;
    static final int MAX_QUESTION_CHARS = 300;
    private static final int ONGOING = 2;

    private final MtPollRepository polls;
    private final MtPollVoteRepository votes;
    private final MeetingInfoRepository meetings;
    private final MeetingPersonalRepository personals;
    private final ObjectMapper mapper;
    private RoomEventPublisher room = RoomEventPublisher.NO_OP;
    private AdminAccess adminAccess;

    public PollServiceImpl(MtPollRepository polls, MtPollVoteRepository votes, MeetingInfoRepository meetings,
                           MeetingPersonalRepository personals, ObjectMapper mapper) {
        this.polls = polls;
        this.votes = votes;
        this.meetings = meetings;
        this.personals = personals;
        this.mapper = mapper;
    }

    @Autowired(required = false)
    void setRoom(RoomEventPublisher room) {
        this.room = room;
    }

    @Autowired(required = false)
    void setAdminAccess(AdminAccess adminAccess) {
        this.adminAccess = adminAccess;
    }

    // ===================== Ghi =====================

    @Override
    @Transactional
    public PollResponse create(String userName, String meetingId, CreatePollRequest request) {
        MeetingInfo meeting = meeting(meetingId);
        if (!canManage(userName, meetingId)) {
            throw new SecurityException("Chỉ chủ trì, đồng chủ trì hoặc thư ký được tạo biểu quyết.");
        }
        if (meeting.getStatus() != ONGOING) {
            throw new IllegalStateException("Chỉ tạo được biểu quyết khi cuộc họp đang diễn ra.");
        }
        String question = request == null || request.question() == null ? "" : request.question().trim();
        if (question.isEmpty() || question.length() > MAX_QUESTION_CHARS) {
            throw new IllegalArgumentException("Câu hỏi biểu quyết không được để trống và tối đa " + MAX_QUESTION_CHARS + " ký tự.");
        }
        Set<String> options = new LinkedHashSet<>();
        if (request.options() != null) {
            for (String o : request.options()) {
                if (o == null || o.isBlank()) continue;
                String t = o.trim();
                if (t.length() > MAX_OPTION_CHARS) throw new IllegalArgumentException("Mỗi phương án tối đa " + MAX_OPTION_CHARS + " ký tự.");
                options.add(t);
            }
        }
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Biểu quyết cần từ " + MIN_OPTIONS + " đến " + MAX_OPTIONS + " phương án khác nhau.");
        }
        MtPoll poll = new MtPoll();
        poll.setId(UUID.randomUUID().toString().replace("-", ""));
        poll.setMeetingId(meetingId);
        poll.setQuestion(question);
        poll.setOptionsJson(write(new ArrayList<>(options)));
        poll.setAnonymous(request.anonymous());
        poll.setStatus(MtPoll.OPEN);
        poll.setCreatedBy(userName);
        poll.setCreatedAt(now());
        polls.save(poll);
        return changed(poll, userName);
    }

    @Override
    @Transactional
    public PollResponse vote(String userName, String meetingId, String pollId, int optionIndex) {
        MeetingInfo meeting = meeting(meetingId);
        MtPoll poll = poll(meetingId, pollId);
        MeetingPersonal me = personals.findByMeetingIdAndUserName(meetingId, userName).orElse(null);
        if (me == null || RoomAdmission.isGuest(userName)) {
            throw new SecurityException("Chỉ thành viên có tài khoản của cuộc họp được bỏ phiếu.");
        }
        if (poll.getStatus() != MtPoll.OPEN || meeting.getStatus() != ONGOING) {
            throw new IllegalStateException("Biểu quyết đã đóng.");
        }
        if (optionIndex < 0 || optionIndex >= options(poll).size()) {
            throw new IllegalArgumentException("Phương án không hợp lệ.");
        }
        MtPollVote vote = votes.findByPollIdAndUserName(pollId, userName).orElseGet(() -> {
            MtPollVote v = new MtPollVote();
            v.setId(UUID.randomUUID().toString().replace("-", ""));
            v.setPollId(pollId);
            v.setUserName(userName);
            return v;
        });
        vote.setOptionIndex(optionIndex);
        vote.setVotedAt(now());
        votes.save(vote);
        return changed(poll, userName);
    }

    @Override
    @Transactional
    public PollResponse close(String userName, String meetingId, String pollId) {
        meeting(meetingId);
        MtPoll poll = poll(meetingId, pollId);
        if (!canManage(userName, meetingId) && !poll.getCreatedBy().equalsIgnoreCase(userName)) {
            throw new SecurityException("Chỉ chủ trì, đồng chủ trì, thư ký hoặc người tạo được đóng biểu quyết.");
        }
        if (poll.getStatus() == MtPoll.OPEN) {
            poll.setStatus(MtPoll.CLOSED);
            poll.setClosedAt(now());
            polls.save(poll);
        }
        return changed(poll, userName);
    }

    @Override
    @Transactional
    public void closeAll(String meetingId) {
        List<MtPoll> open = polls.findByMeetingIdAndStatus(meetingId, MtPoll.OPEN);
        for (MtPoll p : open) {
            p.setStatus(MtPoll.CLOSED);
            p.setClosedAt(now());
        }
        if (!open.isEmpty()) polls.saveAll(open);
    }

    // ===================== Đọc =====================

    @Override
    @Transactional(readOnly = true)
    public List<PollResponse> list(String userName, String meetingId) {
        meeting(meetingId);
        boolean member = personals.findByMeetingIdAndUserName(meetingId, userName).isPresent();
        if (!member && !isAdmin(userName)) {
            throw new SecurityException("Bạn không có quyền xem biểu quyết của cuộc họp này.");
        }
        List<MtPoll> all = polls.findByMeetingIdOrderByCreatedAtAsc(meetingId);
        if (all.isEmpty()) return List.of();
        Map<String, List<MtPollVote>> byPoll = votesByPoll(all);
        Map<String, String> names = names(meetingId);
        boolean manage = canManage(userName, meetingId);
        return all.stream().map(p -> toResponse(p, byPoll.getOrDefault(p.getId(), List.of()), userName, names, manage)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canCreate(String userName, String meetingId) {
        return meeting(meetingId).getStatus() == ONGOING && canManage(userName, meetingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PollSummary> summaries(String meetingId) {
        List<MtPoll> all = polls.findByMeetingIdOrderByCreatedAtAsc(meetingId);
        if (all.isEmpty()) return List.of();
        Map<String, List<MtPollVote>> byPoll = votesByPoll(all);
        List<PollSummary> out = new ArrayList<>();
        for (MtPoll p : all) {
            List<String> opts = options(p);
            List<Integer> counts = counts(opts.size(), byPoll.getOrDefault(p.getId(), List.of()));
            out.add(new PollSummary(p.getQuestion(), opts, counts, counts.stream().mapToInt(Integer::intValue).sum(), p.getStatus() == MtPoll.CLOSED));
        }
        return out;
    }

    // ===================== Nội bộ =====================

    /** Lưu xong thì phát realtime kết quả (mỗi người nhận bản của mình qua list; payload chỉ báo có thay đổi). */
    private PollResponse changed(MtPoll poll, String actor) {
        List<MtPollVote> pv = votes.findByPollIdIn(List.of(poll.getId()));
        PollResponse mine = toResponse(poll, pv, actor, names(poll.getMeetingId()), canManage(actor, poll.getMeetingId()));
        room.broadcastToRoom(poll.getMeetingId(), "PollChanged", Map.of("meetingId", poll.getMeetingId(), "pollId", poll.getId()));
        return mine;
    }

    private PollResponse toResponse(MtPoll p, List<MtPollVote> pv, String viewer, Map<String, String> names, boolean manage) {
        List<String> opts = options(p);
        List<Integer> counts = counts(opts.size(), pv);
        Integer mine = pv.stream().filter(v -> v.getUserName().equalsIgnoreCase(viewer)).map(MtPollVote::getOptionIndex).findFirst().orElse(null);
        List<List<String>> voters = null;
        if (!p.isAnonymous()) {
            voters = new ArrayList<>();
            for (int i = 0; i < opts.size(); i++) voters.add(new ArrayList<>());
            for (MtPollVote v : pv) {
                if (v.getOptionIndex() >= 0 && v.getOptionIndex() < opts.size()) {
                    voters.get(v.getOptionIndex()).add(names.getOrDefault(v.getUserName().toLowerCase(), v.getUserName()));
                }
            }
        }
        int total = counts.stream().mapToInt(Integer::intValue).sum();
        int winner = new PollSummary(p.getQuestion(), opts, counts, total, p.getStatus() == MtPoll.CLOSED).winner();
        return new PollResponse(p.getId(), p.getMeetingId(), p.getQuestion(), opts, p.isAnonymous(), p.getStatus() == MtPoll.CLOSED,
                p.getCreatedBy(), names.getOrDefault(p.getCreatedBy().toLowerCase(), p.getCreatedBy()), p.getCreatedAt(), p.getClosedAt(),
                counts, total, mine, voters, winner, p.getStatus() == MtPoll.OPEN && (manage || p.getCreatedBy().equalsIgnoreCase(viewer)));
    }

    private static List<Integer> counts(int options, List<MtPollVote> pv) {
        int[] c = new int[options];
        for (MtPollVote v : pv) {
            if (v.getOptionIndex() >= 0 && v.getOptionIndex() < options) c[v.getOptionIndex()]++;
        }
        List<Integer> out = new ArrayList<>();
        for (int x : c) out.add(x);
        return out;
    }

    private Map<String, List<MtPollVote>> votesByPoll(List<MtPoll> all) {
        Map<String, List<MtPollVote>> map = new HashMap<>();
        for (MtPollVote v : votes.findByPollIdIn(all.stream().map(MtPoll::getId).toList())) {
            map.computeIfAbsent(v.getPollId(), k -> new ArrayList<>()).add(v);
        }
        return map;
    }

    private Map<String, String> names(String meetingId) {
        Map<String, String> map = new HashMap<>();
        for (MeetingPersonal p : personals.findByMeetingId(meetingId)) {
            map.put(p.getUserName().toLowerCase(), p.getFullName() == null || p.getFullName().isBlank() ? p.getUserName() : p.getFullName());
        }
        return map;
    }

    private boolean canManage(String userName, String meetingId) {
        MeetingPersonal me = personals.findByMeetingIdAndUserName(meetingId, userName).orElse(null);
        if (me != null && (RoomAdmission.isModerator(me) || me.getType() == 2 || me.getType() == 3)) return true;
        return isAdmin(userName);
    }

    private boolean isAdmin(String userName) {
        return adminAccess != null && adminAccess.isAdmin(userName);
    }

    private MeetingInfo meeting(String meetingId) {
        return meetings.findByIdAndDeletedFalse(meetingId).orElseThrow(() -> new IllegalArgumentException("Cuộc họp không tồn tại."));
    }

    private MtPoll poll(String meetingId, String pollId) {
        return polls.findById(pollId).filter(p -> p.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new IllegalArgumentException("Biểu quyết không tồn tại."));
    }

    private List<String> options(MtPoll p) {
        try {
            return mapper.readValue(p.getOptionsJson(), new TypeReference<List<String>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được biểu quyết.", e);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
