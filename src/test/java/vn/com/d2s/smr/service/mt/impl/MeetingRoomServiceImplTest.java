package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.com.d2s.smr.config.AdminAccess;
import vn.com.d2s.smr.dto.mt.room.EnterRoomResponse;
import vn.com.d2s.smr.dto.mt.room.SaveWhiteboardRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MtAttendanceEvent;
import vn.com.d2s.smr.entity.mt.MtLobbyRequest;
import vn.com.d2s.smr.entity.mt.MtWhiteboard;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MtAttendanceEventRepository;
import vn.com.d2s.smr.repository.mt.MtLobbyRequestRepository;
import vn.com.d2s.smr.repository.mt.MtWhiteboardRepository;
import vn.com.d2s.smr.service.ad.PermissionService;
import vn.com.d2s.smr.service.mt.MeetingRoomService;
import vn.com.d2s.smr.service.mt.RoomEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingRoomServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
    private static final LocalDateTime T0 = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock private MeetingInfoRepository meetingRepository;
    @Mock private MeetingPersonalRepository personalRepository;
    @Mock private MtLobbyRequestRepository lobbyRepository;
    @Mock private MtWhiteboardRepository whiteboardRepository;
    @Mock private AdAccountRepository accountRepository;
    @Mock private MtAttendanceEventRepository attendanceRepository;
    @Mock private AdminAccess adminAccess;
    @Mock private PermissionService permissionService;

    private final List<String> sent = new ArrayList<>();
    private MeetingRoomServiceImpl service;
    private RoomAdmission admission;

    @BeforeEach
    void setUp() {
        admission = new RoomAdmission(lobbyRepository, new ObjectMapper());
        MeetingAttendanceService attendance = new MeetingAttendanceService(attendanceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        service = new MeetingRoomServiceImpl(meetingRepository, personalRepository, lobbyRepository, whiteboardRepository,
                accountRepository, admission, attendance, adminAccess, permissionService, Clock.fixed(NOW, ZoneOffset.UTC));
        service.setPublisher(new RoomEventPublisher() {
            @Override public void broadcastToRoom(String meetingId, String target, Object payload) { sent.add("room:" + target); }
            @Override public void sendToUsers(Collection<String> users, String target, Object payload) { sent.add(users + ":" + target); }
            @Override public Set<String> onlineUsers(String meetingId) { return Set.of("host"); }
            @Override public void removeUserFromRoom(String meetingId, String userName) { sent.add("remove:" + userName); }
            @Override public void setWhiteboardLocked(String meetingId, boolean locked) { }
        });
    }

    // ===== Phòng chờ

    @Test
    void enterWithLobbyCreatesPendingRequestAndNotifiesHosts() {
        MeetingInfo m = meeting(2, 0, "{\"lobbyEnabled\":true}");
        stubMembers(m, person("host", 1, true), person("u1", 4, false));
        when(lobbyRepository.findFirstByMeetingIdAndUserNameOrderByRequestedAtDesc("m1", "u1")).thenReturn(Optional.empty());

        EnterRoomResponse res = service.enter("u1", "m1");

        assertThat(res.state()).isEqualTo("LOBBY");
        ArgumentCaptor<MtLobbyRequest> saved = ArgumentCaptor.forClass(MtLobbyRequest.class);
        verify(lobbyRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MtLobbyRequest.PENDING);
        assertThat(sent).contains("[host]:LobbyChanged");
    }

    @Test
    void hostBypassesLobbyAndWaitHostWhenNotStarted() {
        MeetingInfo m = meeting(1, 0, "{\"lobbyEnabled\":true}");
        stubMembers(m, person("host", 1, true), person("u1", 4, false));

        assertThat(service.enter("host", "m1").state()).isEqualTo("ADMITTED");
        assertThat(service.enter("u1", "m1").state()).isEqualTo("WAIT_HOST");
    }

    @Test
    void enterWithoutLobbyAdmitsMember() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true), person("u1", 4, false));
        when(lobbyRepository.findFirstByMeetingIdAndUserNameOrderByRequestedAtDesc("m1", "u1")).thenReturn(Optional.empty());

        assertThat(service.enter("u1", "m1").state()).isEqualTo("ADMITTED");
        verify(lobbyRepository, never()).save(any());
    }

    @Test
    void enterInvitedOnlyMeetingAsOutsiderIsForbidden() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true));

        assertThatThrownBy(() -> service.enter("stranger", "m1")).isInstanceOf(SecurityException.class);
    }

    @Test
    void enterInternalMeetingAsOutsiderAddsOptionalMember() {
        MeetingInfo m = meeting(2, 1, null);
        stubMembers(m, person("host", 1, true));
        AdAccount acc = new AdAccount();
        acc.setUserName("u9");
        acc.setFullName("Người Ngoài");
        when(accountRepository.findById("u9")).thenReturn(Optional.of(acc));
        when(personalRepository.save(any(MeetingPersonal.class))).thenAnswer(i -> i.getArgument(0));
        when(lobbyRepository.findFirstByMeetingIdAndUserNameOrderByRequestedAtDesc("m1", "u9")).thenReturn(Optional.empty());

        EnterRoomResponse res = service.enter("u9", "m1");

        assertThat(res.state()).isEqualTo("ADMITTED");
        ArgumentCaptor<MeetingPersonal> saved = ArgumentCaptor.forClass(MeetingPersonal.class);
        verify(personalRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(5);
    }

    @Test
    void admitSendsDecisionToRequester() {
        MeetingInfo m = meeting(2, 0, "{\"lobbyEnabled\":true}");
        stubMembers(m, person("host", 1, true), person("u1", 4, false));
        MtLobbyRequest r = new MtLobbyRequest();
        r.setId("r1");
        r.setMeetingId("m1");
        r.setUserName("u1");
        r.setStatus(MtLobbyRequest.PENDING);
        when(lobbyRepository.findById("r1")).thenReturn(Optional.of(r));

        service.decide("host", "m1", "r1", true);

        assertThat(r.getStatus()).isEqualTo(MtLobbyRequest.ADMITTED);
        assertThat(sent).contains("[u1]:LobbyDecision");
    }

    // ===== Quyền chủ trì (tiêu chí: user thường gọi remove → 403)

    @Test
    void nonModeratorCannotKickMuteOrAdmit() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true), person("u1", 4, false), person("u2", 3, false));

        assertThatThrownBy(() -> service.kick("u1", "m1", "u2")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.mute("u1", "m1", "u2")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.admitAll("u2", "m1")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.changeRole("u1", "m1", "u2", 2)).isInstanceOf(SecurityException.class);
    }

    @Test
    void coHostManagesRoomButCannotTouchOwner() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true), person("co", 2, true), person("u1", 4, false));

        // Đồng chủ trì có quyền quản lý như chủ trì...
        service.changeRole("co", "m1", "u1", 3);
        service.kick("co", "m1", "u1");
        // ...nhưng không mời ra / đổi vai trò người tạo, và không ai được gán "Chủ trì".
        assertThatThrownBy(() -> service.kick("co", "m1", "host")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.changeRole("co", "m1", "host", 4)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.changeRole("host", "m1", "u1", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantingCoHostGivesManagementFlag() {
        MeetingInfo m = meeting(2, 0, null);
        MeetingPersonal u1 = person("u1", 4, false);
        stubMembers(m, person("host", 1, true), u1);

        service.changeRole("host", "m1", "u1", 2);

        assertThat(u1.getType()).isEqualTo(2);
        assertThat(u1.isChairperson()).isTrue();
        assertThat(RoomAdmission.isModerator(u1)).isTrue();
    }

    @Test
    void kickRecordsRejectionAndRemovesFromRoom() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true), person("u1", 4, false));

        service.kick("host", "m1", "u1");

        ArgumentCaptor<MtLobbyRequest> saved = ArgumentCaptor.forClass(MtLobbyRequest.class);
        verify(lobbyRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(MtLobbyRequest.REJECTED);
        assertThat(sent).contains("[u1]:Kicked", "remove:u1");
    }

    @Test
    void cannotDemoteLastHost() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true));

        assertThatThrownBy(() -> service.changeRole("host", "m1", "host", 4)).isInstanceOf(IllegalStateException.class);
    }

    // ===== Bảng trắng

    @Test
    void saveWhiteboardWithStaleRevisionConflicts() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true), person("u1", 4, false));
        MtWhiteboard board = new MtWhiteboard();
        board.setId("w1");
        board.setMeetingId("m1");
        board.setRevision(3);
        when(whiteboardRepository.findFirstByMeetingId("m1")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> service.saveWhiteboard("u1", "m1", new SaveWhiteboardRequest(2, "[]")))
                .isInstanceOf(MeetingRoomService.WhiteboardConflictException.class);

        assertThat(service.saveWhiteboard("u1", "m1", new SaveWhiteboardRequest(3, "[1]")).revision()).isEqualTo(4);
        assertThat(sent).contains("room:WhiteboardSaved");
    }

    @Test
    void lockedWhiteboardRejectsNonModerator() {
        MeetingInfo m = meeting(2, 0, null);
        stubMembers(m, person("host", 1, true), person("u1", 4, false));
        MtWhiteboard board = new MtWhiteboard();
        board.setMeetingId("m1");
        board.setLocked(true);
        when(whiteboardRepository.findFirstByMeetingId("m1")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> service.saveWhiteboard("u1", "m1", new SaveWhiteboardRequest(0, "[]")))
                .isInstanceOf(SecurityException.class);
        assertThat(service.saveWhiteboard("host", "m1", new SaveWhiteboardRequest(0, "[]")).revision()).isEqualTo(1);
    }

    // ===== Tham dự (tiêu chí: vào/rời/vào lại 3 lần không tính trùng)

    @Test
    void attendanceMergesReentriesAndParallelTabs() {
        MeetingInfo m = meeting(3, 0, null);
        m.setEndDate(T0.plusMinutes(60));
        stubMembers(m, person("host", 1, true), person("u1", 4, false));
        when(attendanceRepository.findByMeetingIdOrderByOccurredAtAsc("m1")).thenReturn(List.of(
                event("u1", 0, 0, "a"), event("u1", 1, 10, "a"),      // 0–10
                event("u1", 0, 20, "b"), event("u1", 1, 30, "b"),     // 20–30
                event("u1", 0, 25, "c"), event("u1", 1, 35, "c"),     // tab song song 25–35 → hợp thành 20–35
                event("u1", 0, 50, "d")                                // mất kết nối, chưa rời → tới lúc kết thúc (60)
        ));
        when(adminAccess.isAdmin("host")).thenReturn(false);

        var rows = service.attendance("host", "m1");
        var u1 = rows.stream().filter(r -> r.userName().equals("u1")).findFirst().orElseThrow();

        assertThat(u1.totalSeconds()).isEqualTo((10 + 15 + 10) * 60L);
        assertThat(u1.joinCount()).isEqualTo(4);
        assertThat(rows.stream().filter(r -> r.userName().equals("host")).findFirst().orElseThrow().totalSeconds()).isZero();
    }

    @Test
    void attendanceForbiddenForPlainMember() {
        MeetingInfo m = meeting(3, 0, null);
        stubMembers(m, person("host", 1, true), person("u1", 4, false));

        assertThatThrownBy(() -> service.attendance("u1", "m1")).isInstanceOf(SecurityException.class);
    }

    @Test
    void exportAttendanceProducesXlsx() {
        MeetingInfo m = meeting(3, 0, null);
        m.setRoomCode("ABC123");
        m.setEndDate(T0.plusMinutes(30));
        stubMembers(m, person("host", 1, true));
        when(attendanceRepository.findByMeetingIdOrderByOccurredAtAsc("m1")).thenReturn(List.of(event("host", 0, 0, "a")));

        MeetingRoomService.ExportedFile file = service.exportAttendance("host", "m1");

        assertThat(file.fileName()).startsWith("ThamDu_ABC123");
        // XLSX là file ZIP → bắt đầu bằng "PK"
        assertThat(file.content()[0]).isEqualTo((byte) 'P');
        assertThat(file.content()[1]).isEqualTo((byte) 'K');
    }

    // ===== /join/:roomCode

    @Test
    void publicInfoHidesDetailsWhenGuestsNotAllowed() {
        MeetingInfo publicMeeting = meeting(2, 2, null);
        MeetingInfo internal = meeting(2, 1, null);
        internal.setId("m2");
        when(meetingRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("PUB")).thenReturn(Optional.of(publicMeeting));
        when(meetingRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("INT")).thenReturn(Optional.of(internal));
        when(personalRepository.findByMeetingId("m1")).thenReturn(List.of(person("host", 1, true)));

        assertThat(service.publicInfo("PUB").guestAllowed()).isTrue();
        assertThat(service.publicInfo("PUB").name()).isEqualTo("Họp phòng");
        var internalInfo = service.publicInfo("INT");
        assertThat(internalInfo.reason()).isEqualTo("GUEST_NOT_ALLOWED");
        assertThat(internalInfo.name()).isNull();
        assertThat(service.publicInfo("NONE").reason()).isEqualTo("NOT_FOUND");
    }

    // ===== Tiện ích

    private MeetingInfo meeting(int status, int visibility, String settingsJson) {
        MeetingInfo m = new MeetingInfo();
        m.setId("m1");
        m.setName("Họp phòng");
        m.setStatus(status);
        m.setVisibility(visibility);
        m.setRoomCode("room1");
        m.setExpectedStartTime(T0);
        m.setExpectedEndTime(T0.plusHours(1));
        if (status == 2 || status == 3) m.setStartDate(T0);
        if (settingsJson != null) m.setSettingsJson(settingsJson);
        when(meetingRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(m));
        return m;
    }

    private void stubMembers(MeetingInfo m, MeetingPersonal... people) {
        when(personalRepository.findByMeetingId(m.getId())).thenReturn(List.of(people));
        for (MeetingPersonal p : people) {
            when(personalRepository.findByMeetingIdAndUserName(m.getId(), p.getUserName())).thenReturn(Optional.of(p));
        }
    }

    private static MeetingPersonal person(String userName, int type, boolean chair) {
        MeetingPersonal p = new MeetingPersonal();
        p.setMeetingId("m1");
        p.setUserName(userName);
        p.setFullName(userName.toUpperCase());
        p.setType(type);
        p.setChairperson(chair);
        return p;
    }

    private static MtAttendanceEvent event(String user, int type, int minute, String session) {
        MtAttendanceEvent e = new MtAttendanceEvent();
        e.setMeetingId("m1");
        e.setUserName(user);
        e.setEventType(type);
        e.setOccurredAt(T0.plusMinutes(minute));
        e.setClientSessionId(session);
        return e;
    }
}
