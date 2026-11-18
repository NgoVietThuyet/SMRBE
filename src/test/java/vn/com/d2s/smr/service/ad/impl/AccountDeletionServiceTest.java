package vn.com.d2s.smr.service.ad.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.dto.ad.employee.DeleteAccountRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.ad.AdFaceTemplateRepository;
import vn.com.d2s.smr.repository.mt.MeetingAuditLogRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.nt.NotificationRepository;
import vn.com.d2s.smr.service.mt.MeetingPurgeService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    AdAccountRepository accounts = mock(AdAccountRepository.class);
    MeetingInfoRepository meetings = mock(MeetingInfoRepository.class);
    MeetingPersonalRepository personals = mock(MeetingPersonalRepository.class);
    MeetingPurgeService purge = mock(MeetingPurgeService.class);
    AdFaceTemplateRepository faces = mock(AdFaceTemplateRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(accounts, meetings, personals, mock(MeetingAuditLogRepository.class), faces, notifications, purge);
        when(accounts.findById("boss")).thenReturn(Optional.of(account("boss", null)));
        when(accounts.findById("deputy")).thenReturn(Optional.of(account("deputy", null)));
    }

    static AdAccount account(String user, String roles) {
        AdAccount a = new AdAccount();
        a.setUserName(user);
        a.setRoleCodes(roles);
        a.setActive(true);
        return a;
    }

    static MeetingInfo meeting(String id, int status) {
        MeetingInfo m = new MeetingInfo();
        m.setId(id);
        m.setName("Họp " + id);
        m.setStatus(status);
        return m;
    }

    static MeetingPersonal person(String meetingId, String user, int type) {
        MeetingPersonal p = new MeetingPersonal();
        p.setMeetingId(meetingId);
        p.setUserName(user);
        p.setType(type);
        return p;
    }

    private void bossOwns(MeetingInfo... ms) {
        List<MeetingPersonal> rows = new java.util.ArrayList<>();
        for (MeetingInfo m : ms) {
            rows.add(person(m.getId(), "boss", 1));
            when(meetings.findByIdAndDeletedFalse(m.getId())).thenReturn(Optional.of(m));
            when(personals.findByMeetingIdAndUserName(m.getId(), "deputy")).thenReturn(Optional.of(person(m.getId(), "deputy", 4)));
        }
        when(personals.findByUserNameIgnoreCase("boss")).thenReturn(rows);
    }

    @Test
    void transferKeepsMeetingAndNoTransferDeletesIt() {
        MeetingInfo keep = meeting("m1", 1);
        MeetingInfo drop = meeting("m2", 0);
        bossOwns(keep, drop);
        MeetingPersonal receiver = person("m1", "deputy", 4);
        when(personals.findByMeetingIdAndUserName("m1", "deputy")).thenReturn(Optional.of(receiver));

        service.delete("boss", new DeleteAccountRequest(List.of(new DeleteAccountRequest.Transfer("m1", "deputy"))), "admin");

        assertThat(receiver.getType()).isEqualTo(1);
        assertThat(keep.getCreateBy()).isEqualTo("deputy");
        assertThat(keep.isDeleted()).isFalse();
        assertThat(drop.isDeleted()).isTrue();
        verify(purge).purge(drop);
        verify(purge, never()).purge(keep);
        verify(personals).deleteByMeetingIdAndUserName("m1", "boss");
        verify(faces).deleteByUserNameIgnoreCase("boss");
        verify(accounts).delete(any(AdAccount.class));
    }

    @Test
    void finishedMeetingIsKeptAndNotAskedAbout() {
        MeetingInfo done = meeting("m3", 3);
        bossOwns(done);

        assertThat(service.ownedMeetings("boss")).isEmpty();
        service.delete("boss", new DeleteAccountRequest(null), "admin");

        assertThat(done.isDeleted()).isFalse();
        verify(purge, never()).purge(any());
        verify(accounts).delete(any(AdAccount.class));
    }

    @Test
    void rejectsGuestOrNonMemberReceiverBeforeChangingAnything() {
        MeetingInfo m = meeting("m1", 1);
        bossOwns(m);
        when(personals.findByMeetingIdAndUserName("m1", "stranger")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("boss",
                new DeleteAccountRequest(List.of(new DeleteAccountRequest.Transfer("m1", "stranger"))), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accounts, never()).delete(any(AdAccount.class));
        assertThat(m.isDeleted()).isFalse();
    }

    @Test
    void blocksSelfAndSuperAdmin() {
        assertThatThrownBy(() -> service.delete("boss", new DeleteAccountRequest(null), "boss")).isInstanceOf(IllegalStateException.class);
        when(accounts.findById("root")).thenReturn(Optional.of(account("root", "SUPER_ADMIN")));
        assertThatThrownBy(() -> service.delete("root", new DeleteAccountRequest(null), "admin")).isInstanceOf(IllegalStateException.class);
    }
}
