package vn.com.d2s.smr.service.mt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.CancelMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.CreateMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.MeetingCodeLookupResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDashboardResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDetailResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingJoinInfoResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingListItemResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSearchRequest;
import vn.com.d2s.smr.dto.mt.meeting.QuickMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.ScheduleConflictRequest;
import vn.com.d2s.smr.dto.mt.meeting.ScheduleConflictResponse;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingParticipantsRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingRequest;
import vn.com.d2s.smr.dto.mt.message.MeetingMessageResponse;
import vn.com.d2s.smr.dto.mt.message.SendMeetingMessageRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingMessage;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingAuditLogRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingMessageRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-30T01:00:00Z");

    @Mock
    private MeetingInfoRepository meetingInfoRepository;

    @Mock
    private MeetingPersonalRepository meetingPersonalRepository;

    @Mock
    private MeetingAuditLogRepository meetingAuditLogRepository;

    @Mock
    private MeetingMessageRepository meetingMessageRepository;

    @Mock
    private AdAccountRepository accountRepository;

    private MeetingServiceImpl meetingService;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingServiceImpl(
                meetingInfoRepository,
                meetingPersonalRepository,
                meetingAuditLogRepository,
                meetingMessageRepository,
                accountRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "meet.d2s.vn",
                "https://meet.d2s.vn/external_api.js"
        );
    }

    @Test
    void getDashboardReturnsCalculatedMetrics() {
        when(meetingInfoRepository.countByStatusAndUser(1, "user1")).thenReturn(3);
        when(meetingInfoRepository.countByStatusAndUser(2, "user1")).thenReturn(1);
        when(meetingInfoRepository.countByStatusAndUser(3, "user1")).thenReturn(5);
        when(meetingInfoRepository.countByStatusAndUser(4, "user1")).thenReturn(0);
        // NOW = 01:00 UTC 30/09 = 08:00 giờ VN → "đầu hôm nay" = 00:00 VN 30/09 = 17:00 UTC 29/09.
        when(meetingInfoRepository.findNextMeetings(eq("user1"), eq(LocalDateTime.of(2026, 9, 29, 17, 0)), any()))
                .thenReturn(List.of());

        MeetingDashboardResponse response = meetingService.getDashboard("user1");

        assertThat(response.upcoming()).isEqualTo(3);
        assertThat(response.ongoing()).isEqualTo(1);
        assertThat(response.ended()).isEqualTo(5);
        assertThat(response.cancelled()).isEqualTo(0);
    }

    @Test
    void lookupByRoomCodeLetsMemberJoinScheduledMeeting() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        when(meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("ROOM1234")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(
                samplePersonal("m1", "host", 1, true), samplePersonal("m1", "user1", 2, false)));

        MeetingCodeLookupResponse res = meetingService.lookupByRoomCode("user1", " ROOM1234 ");

        assertThat(res.canJoin()).isTrue();
        assertThat(res.reason()).isNull();
        assertThat(res.meetingId()).isEqualTo("m1");
        assertThat(res.member()).isTrue();
    }

    @Test
    void lookupByRoomCodeHidesDetailsFromNonMember() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        when(meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("room1234")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(samplePersonal("m1", "host", 1, true)));

        MeetingCodeLookupResponse res = meetingService.lookupByRoomCode("stranger", "room1234");

        assertThat(res.canJoin()).isFalse();
        assertThat(res.reason()).isEqualTo("NOT_INVITED");
        assertThat(res.meetingId()).isNull();
        assertThat(res.name()).isNull();
    }

    @Test
    void lookupByRoomCodeReportsCancelledEndedDraftAndMissing() {
        when(meetingPersonalRepository.findByMeetingId(any())).thenReturn(List.of(samplePersonal("m", "user1", 2, false)));

        when(meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("C")).thenReturn(Optional.of(sampleMeeting("m", 4)));
        assertThat(meetingService.lookupByRoomCode("user1", "C").reason()).isEqualTo("CANCELLED");

        when(meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("E")).thenReturn(Optional.of(sampleMeeting("m", 3)));
        assertThat(meetingService.lookupByRoomCode("user1", "E").reason()).isEqualTo("ENDED");

        when(meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("D")).thenReturn(Optional.of(sampleMeeting("m", 0)));
        assertThat(meetingService.lookupByRoomCode("user1", "D").reason()).isEqualTo("NOT_FOUND");

        when(meetingInfoRepository.findFirstByRoomCodeIgnoreCaseAndDeletedFalse("X")).thenReturn(Optional.empty());
        assertThat(meetingService.lookupByRoomCode("user1", "X").reason()).isEqualTo("NOT_FOUND");
        assertThat(meetingService.lookupByRoomCode("user1", "   ").reason()).isEqualTo("NOT_FOUND");
    }

    @Test
    void searchMeetingsReturnsPagedList() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        when(meetingInfoRepository.searchMeetings(eq("user1"), eq(false), eq(List.of(1)), eq(""), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(meeting)));

        PagedResultResponse<MeetingListItemResponse> result = meetingService.searchMeetings(
                "user1",
                new MeetingSearchRequest("upcoming", "", null, null, 1, 10, null, null)
        );

        assertThat(result.totalItems()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo("m1");
    }

    @Test
    void getMeetingDetailReturnsDetailForMember() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal personal = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(personal));

        Optional<MeetingDetailResponse> detailOpt = meetingService.getMeetingDetail("user1", "m1");

        assertThat(detailOpt).isPresent();
        assertThat(detailOpt.get().id()).isEqualTo("m1");
        assertThat(detailOpt.get().host()).isTrue();
    }

    @Test
    void getMeetingDetailThrowsSecurityExceptionForNonMember() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal personal = samplePersonal("m1", "otherUser", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(personal));

        assertThatThrownBy(() -> meetingService.getMeetingDetail("user1", "m1"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bạn không có quyền truy cập");
    }

    @Test
    void createMeetingSavesMeetingAndHostParticipant() {
        AdAccount host = sampleAccount("user1");
        when(accountRepository.findById("user1")).thenReturn(Optional.of(host));

        CreateMeetingRequest request = new CreateMeetingRequest(
                "Họp chiến lược",
                "Mô tả",
                "Chương trình",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(2),
                "Asia/Bangkok",
                1,
                false,
                true,
                null,
                List.of(),
                List.of()
        );

        MeetingDetailResponse response = meetingService.createMeeting("user1", request);

        assertThat(response.name()).isEqualTo("Họp chiến lược");
        assertThat(response.host()).isTrue();
        verify(meetingInfoRepository).save(any(MeetingInfo.class));
        verify(meetingPersonalRepository).saveAll(any());
    }

    @Test
    void updateMeetingSavesChangesForHost() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "m1",
                "Tên mới",
                "Mô tả mới",
                "Agenda mới",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                "Asia/Bangkok",
                1,
                null,
                null,
                null,
                null
        );

        MeetingDetailResponse updated = meetingService.updateMeeting("user1", "m1", request);

        assertThat(updated.name()).isEqualTo("Tên mới");
        verify(meetingInfoRepository).save(meeting);
    }

    @Test
    void deleteMeetingSoftDeletesMeeting() {
        MeetingInfo meeting = sampleMeeting("m1", 0);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        meetingService.deleteMeeting("user1", "m1");

        assertThat(meeting.isDeleted()).isTrue();
        verify(meetingInfoRepository).save(meeting);
    }

    @Test
    void cancelMeetingChangesStatusToCancelled() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        meetingService.cancelMeeting("user1", "m1", new CancelMeetingRequest("m1", "Bận đột xuất"));

        assertThat(meeting.getStatus()).isEqualTo(4);
        assertThat(meeting.getCancellationReason()).isEqualTo("Bận đột xuất");
        verify(meetingInfoRepository).save(meeting);
    }

    @Test
    void cancelMeetingPublishesNotificationEvent() {
        List<Object> published = new java.util.ArrayList<>();
        meetingService.setEventPublisher(published::add);
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        meetingService.cancelMeeting("user1", "m1", new CancelMeetingRequest("m1", "Bận đột xuất"));

        assertThat(published).containsExactly(new NotificationEvents.MeetingCancelled("m1", "user1", "Bận đột xuất"));
    }

    @Test
    void archiveMeetingArchivesEndedMeeting() {
        MeetingInfo meeting = sampleMeeting("m1", 3);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        meetingService.archiveMeeting("user1", "m1");

        assertThat(meeting.isArchived()).isTrue();
        assertThat(meeting.getStatus()).isEqualTo(5);
        verify(meetingInfoRepository).save(meeting);
    }

    @Test
    void createQuickMeetingCreatesOngoingMeeting() {
        AdAccount host = sampleAccount("user1");
        when(accountRepository.findById("user1")).thenReturn(Optional.of(host));

        QuickMeetingRequest request = new QuickMeetingRequest("Họp nhanh", List.of());

        MeetingDetailResponse response = meetingService.createQuickMeeting("user1", request);

        assertThat(response.status()).isEqualTo(2);
        assertThat(response.host()).isTrue();
        verify(meetingInfoRepository).save(any(MeetingInfo.class));
    }

    @Test
    void addParticipantsAddsNewMembers() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        UpdateMeetingParticipantsRequest request = new UpdateMeetingParticipantsRequest(List.of(), List.of("user2"));

        MeetingDetailResponse response = meetingService.addParticipants("user1", "m1", request);

        assertThat(response.id()).isEqualTo("m1");
        verify(meetingPersonalRepository).saveAll(any());
    }

    @Test
    void removeParticipantDeletesMember() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);
        MeetingPersonal member = samplePersonal("m1", "user2", 2, false);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host, member));

        meetingService.removeParticipant("user1", "m1", "user2");

        verify(meetingPersonalRepository).deleteByMeetingIdAndUserName("m1", "user2");
    }

    @Test
    void getJoinInfoReturnsJoinInfoResponse() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        MeetingJoinInfoResponse response = meetingService.getJoinInfo("user1", "m1");

        assertThat(response.meetingId()).isEqualTo("m1");
        assertThat(response.roomName()).isEqualTo("room1234");
        assertThat(response.host()).isTrue();
        assertThat(response.domain()).isEqualTo("meet.d2s.vn");
        assertThat(response.jitsiDomain()).isEqualTo("meet.d2s.vn");
        assertThat(response.externalApiUrl()).isEqualTo("https://meet.d2s.vn/external_api.js");
    }

    @Test
    void startMeetingUpdatesStatusToOngoing() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        meetingService.startMeeting("user1", "m1");

        assertThat(meeting.getStatus()).isEqualTo(2);
        verify(meetingInfoRepository).save(meeting);
    }

    @Test
    void endMeetingUpdatesStatusToEnded() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        MeetingPersonal host = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(host));

        meetingService.endMeeting("user1", "m1");

        assertThat(meeting.getStatus()).isEqualTo(3);
        verify(meetingInfoRepository).save(meeting);
    }

    @Test
    void joinMeetingUpdatesJoinedStatus() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        MeetingPersonal personal = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingIdAndUserName("m1", "user1")).thenReturn(Optional.of(personal));

        meetingService.joinMeeting("user1", "m1");

        assertThat(personal.isJoined()).isTrue();
        verify(meetingPersonalRepository).save(personal);
    }

    @Test
    void leaveMeetingUpdatesJoinedStatusToFalse() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        MeetingPersonal personal = samplePersonal("m1", "user1", 1, true);
        personal.setJoined(true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingIdAndUserName("m1", "user1")).thenReturn(Optional.of(personal));

        meetingService.leaveMeeting("user1", "m1");

        assertThat(personal.isJoined()).isFalse();
        verify(meetingPersonalRepository).save(personal);
    }

    @Test
    void getMessagesReturnsMessageList() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        MeetingPersonal personal = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.existsByMeetingIdAndUserName("m1", "user1")).thenReturn(true);
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(personal));

        MeetingMessage msg = new MeetingMessage();
        msg.setId("msg1");
        msg.setMeetingId("m1");
        msg.setSenderUserId("user1");
        msg.setReceiverUserId("");
        msg.setMessageText("Hello world");
        msg.setCreateDate(LocalDateTime.now());

        when(meetingMessageRepository.findByMeetingIdOrderByCreateDateAsc("m1")).thenReturn(List.of(msg));

        List<MeetingMessageResponse> messages = meetingService.getMessages("user1", "m1");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).messageText()).isEqualTo("Hello world");
    }

    @Test
    void sendMessageSavesAndReturnsMessageResponse() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        MeetingPersonal personal = samplePersonal("m1", "user1", 1, true);

        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingIdAndUserName("m1", "user1")).thenReturn(Optional.of(personal));

        SendMeetingMessageRequest req = new SendMeetingMessageRequest("", "Xin chào mọi người");

        MeetingMessageResponse response = meetingService.sendMessage("user1", "m1", req);

        assertThat(response.messageText()).isEqualTo("Xin chào mọi người");
        verify(meetingMessageRepository).save(any(MeetingMessage.class));
    }

    // ===== P9 =====

    @Test
    void statusesForTabMapsTabsAndNarrowsByStatus() {
        assertThat(MeetingServiceImpl.statusesForTab("all", null)).containsExactly(0, 1, 2, 3, 4);
        assertThat(MeetingServiceImpl.statusesForTab("upcoming", null)).containsExactly(1);
        assertThat(MeetingServiceImpl.statusesForTab("history", null)).containsExactly(3, 4);
        assertThat(MeetingServiceImpl.statusesForTab("history", 4)).containsExactly(4);
        assertThat(MeetingServiceImpl.statusesForTab("archived", null)).containsExactly(5);
        // status nằm ngoài tab → không có kết quả
        assertThat(MeetingServiceImpl.statusesForTab("upcoming", 3)).containsExactly(-1);
    }

    @Test
    void deleteMeetingRejectsPublishedMeeting() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(samplePersonal("m1", "user1", 1, true)));

        assertThatThrownBy(() -> meetingService.deleteMeeting("user1", "m1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(meeting.isDeleted()).isFalse();
    }

    @Test
    void archiveMeetingRejectsScheduledMeeting() {
        MeetingInfo meeting = sampleMeeting("m1", 1);
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(samplePersonal("m1", "user1", 1, true)));

        assertThatThrownBy(() -> meetingService.archiveMeeting("user1", "m1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelMeetingRejectsOngoingMeeting() {
        MeetingInfo meeting = sampleMeeting("m1", 2);
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(samplePersonal("m1", "user1", 1, true)));

        assertThatThrownBy(() -> meetingService.cancelMeeting("user1", "m1", new CancelMeetingRequest("m1", "x")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createMeetingRejectsEndBeforeStart() {
        when(accountRepository.findById("user1")).thenReturn(Optional.of(sampleAccount("user1")));
        LocalDateTime start = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        CreateMeetingRequest request = new CreateMeetingRequest("Họp", "", "", start, start.minusMinutes(5),
                "Asia/Bangkok", 0, false, true, null, List.of(), List.of());

        assertThatThrownBy(() -> meetingService.createMeeting("user1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Giờ kết thúc");
    }

    @Test
    void createDraftDoesNotSendInvitations() {
        List<Object> published = new java.util.ArrayList<>();
        meetingService.setEventPublisher(published::add);
        when(accountRepository.findById("user1")).thenReturn(Optional.of(sampleAccount("user1")));
        LocalDateTime start = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        CreateMeetingRequest request = new CreateMeetingRequest("Họp nháp", "", "", start, start.plusHours(1),
                "Asia/Bangkok", 0, true, false, null, List.of(), List.of("user2"));

        MeetingDetailResponse response = meetingService.createMeeting("user1", request);

        assertThat(response.status()).isZero();
        assertThat(published).isEmpty();
    }

    @Test
    void updateMeetingPublishesDraftAndInvites() {
        List<Object> published = new java.util.ArrayList<>();
        meetingService.setEventPublisher(published::add);
        MeetingInfo meeting = sampleMeeting("m1", 0);
        meeting.setDraft(true);
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(meeting));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(
                samplePersonal("m1", "user1", 1, true), samplePersonal("m1", "user2", 2, false)));

        UpdateMeetingRequest request = new UpdateMeetingRequest("m1", "Họp", null, null, null, null, null, 0,
                null, null, null, null, true);
        MeetingDetailResponse updated = meetingService.updateMeeting("user1", "m1", request);

        assertThat(updated.status()).isEqualTo(1);
        assertThat(meeting.isDraft()).isFalse();
        assertThat(published).containsExactly(new NotificationEvents.MeetingInvited("m1", "user1", List.of("user2")));
    }

    @Test
    void findScheduleConflictsIncludesCurrentUserAndMapsRows() {
        LocalDateTime start = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        MeetingInfo other = sampleMeeting("m2", 1);
        MeetingPersonal person = samplePersonal("m2", "User2", 2, false);
        when(meetingInfoRepository.findScheduleConflicts(eq(java.util.Set.of("user1", "user2")), eq(start),
                eq(start.plusHours(1)), eq("m1")))
                .thenReturn(java.util.Collections.singletonList(new Object[]{other, person}));

        List<ScheduleConflictResponse> result = meetingService.findScheduleConflicts("user1",
                new ScheduleConflictRequest(start, start.plusHours(1), List.of("User2", " "), "m1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).meetingId()).isEqualTo("m2");
        assertThat(result.get(0).userName()).isEqualTo("User2");
    }

    @Test
    void findScheduleConflictsIgnoresInvalidRange() {
        LocalDateTime start = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        assertThat(meetingService.findScheduleConflicts("user1",
                new ScheduleConflictRequest(start, start, List.of(), null))).isEmpty();
    }

    @Test
    void getJoinInfoRejectsCancelledMeeting() {
        when(meetingInfoRepository.findByIdAndDeletedFalse("m1")).thenReturn(Optional.of(sampleMeeting("m1", 4)));
        when(meetingPersonalRepository.findByMeetingId("m1")).thenReturn(List.of(samplePersonal("m1", "user1", 1, true)));

        assertThatThrownBy(() -> meetingService.getJoinInfo("user1", "m1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MeetingInfo sampleMeeting(String id, int status) {
        MeetingInfo m = new MeetingInfo();
        m.setId(id);
        m.setName("Họp mẫu");
        m.setStatus(status);
        m.setExpectedStartTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        m.setExpectedEndTime(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(1));
        m.setRoomCode("room1234");
        m.setJoinUrl("/meeting/room1234");
        m.setReferenceFileId("ref1");
        m.setDeleted(false);
        m.setArchived(false);
        m.setVersion(1);
        return m;
    }

    private static MeetingPersonal samplePersonal(String meetingId, String userName, int type, boolean isChuTri) {
        MeetingPersonal p = new MeetingPersonal();
        p.setId("p1");
        p.setMeetingId(meetingId);
        p.setUserName(userName);
        p.setFullName("User Full Name");
        p.setType(type);
        p.setChairperson(isChuTri);
        return p;
    }

    private static AdAccount sampleAccount(String userName) {
        AdAccount account = new AdAccount();
        account.setUserName(userName);
        account.setFullName("User Host Name");
        account.setEmail("user1@example.com");
        return account;
    }
}
