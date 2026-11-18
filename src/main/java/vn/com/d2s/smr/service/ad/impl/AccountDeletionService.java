package vn.com.d2s.smr.service.ad.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.config.AdminAccess;
import vn.com.d2s.smr.dto.ad.employee.DeleteAccountRequest;
import vn.com.d2s.smr.dto.ad.employee.OwnedMeetingResponse;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingAuditLog;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.ad.AdFaceTemplateRepository;
import vn.com.d2s.smr.repository.mt.MeetingAuditLogRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.nt.NotificationRepository;
import vn.com.d2s.smr.service.mt.MeetingPurgeService;
import vn.com.d2s.smr.service.mt.impl.RoomAdmission;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Xóa tài khoản. Cuộc họp chưa kết thúc (nháp, sắp diễn ra, đang diễn ra) mà tài khoản là chủ trì: chuyển chủ trì cho thành viên
 * được chọn, không chọn thì xóa cuộc họp kèm biên bản, bản ghi. Cuộc họp đã kết thúc/hủy/lưu trữ giữ nguyên làm lịch sử.
 */
@Service
public class AccountDeletionService {

    private final AdAccountRepository accounts;
    private final MeetingInfoRepository meetings;
    private final MeetingPersonalRepository personals;
    private final MeetingAuditLogRepository audits;
    private final AdFaceTemplateRepository faces;
    private final NotificationRepository notifications;
    private final MeetingPurgeService purge;

    public AccountDeletionService(AdAccountRepository accounts, MeetingInfoRepository meetings, MeetingPersonalRepository personals,
                                  MeetingAuditLogRepository audits, AdFaceTemplateRepository faces,
                                  NotificationRepository notifications, MeetingPurgeService purge) {
        this.accounts = accounts;
        this.meetings = meetings;
        this.personals = personals;
        this.audits = audits;
        this.faces = faces;
        this.notifications = notifications;
        this.purge = purge;
    }

    @Transactional(readOnly = true)
    public List<OwnedMeetingResponse> ownedMeetings(String userName) {
        List<OwnedMeetingResponse> out = new ArrayList<>();
        for (MeetingInfo m : ownedUnfinished(userName)) {
            List<OwnedMeetingResponse.Candidate> candidates = new ArrayList<>();
            for (MeetingPersonal p : personals.findByMeetingId(m.getId())) {
                if (p.getUserName().equalsIgnoreCase(userName) || RoomAdmission.isGuest(p.getUserName())) continue;
                if (accounts.findById(p.getUserName()).map(AdAccount::isActive).orElse(false)) {
                    candidates.add(new OwnedMeetingResponse.Candidate(p.getUserName(),
                            p.getFullName() == null || p.getFullName().isBlank() ? p.getUserName() : p.getFullName()));
                }
            }
            out.add(new OwnedMeetingResponse(m.getId(), m.getName(), m.getStatus(), m.getExpectedStartTime(), candidates));
        }
        return out;
    }

    @Transactional
    public void delete(String userName, DeleteAccountRequest request, String actor) {
        if (userName.equalsIgnoreCase(actor)) throw new IllegalStateException("Không thể tự xóa tài khoản đang đăng nhập.");
        AdAccount account = accounts.findById(userName).orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản."));
        if (AdminAccess.hasSuperAdminRole(account)) throw new IllegalStateException("Không thể xóa tài khoản quản trị.");

        Map<String, String> transfers = new HashMap<>();
        request.safeTransfers().forEach(t -> {
            if (t.meetingId() != null && t.newOwner() != null && !t.newOwner().isBlank()) transfers.put(t.meetingId(), t.newOwner());
        });
        List<MeetingInfo> owned = ownedUnfinished(userName);
        // Kiểm tra hết trước khi sửa gì để lỗi không để lại dữ liệu dở dang.
        Map<String, MeetingPersonal> receivers = new HashMap<>();
        for (MeetingInfo m : owned) {
            String to = transfers.get(m.getId());
            if (to == null) continue;
            MeetingPersonal receiver = personals.findByMeetingIdAndUserName(m.getId(), to)
                    .filter(p -> !p.getUserName().equalsIgnoreCase(userName) && !RoomAdmission.isGuest(p.getUserName()))
                    .filter(p -> accounts.findById(p.getUserName()).map(AdAccount::isActive).orElse(false))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Người nhận chủ trì phải là thành viên đang hoạt động của cuộc họp \"" + m.getName() + "\"."));
            receivers.put(m.getId(), receiver);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (MeetingInfo m : owned) {
            MeetingPersonal receiver = receivers.get(m.getId());
            if (receiver != null) {
                receiver.setType(1);
                receiver.setChairperson(true);
                personals.save(receiver);
                personals.deleteByMeetingIdAndUserName(m.getId(), userName);
                m.setCreateBy(receiver.getUserName());
                m.setUpdateBy(actor);
                m.setUpdateDate(now);
                meetings.save(m);
                audit(m.getId(), "TRANSFER_HOST", actor, m.getVersion() + 1,
                        "Chuyển chủ trì từ " + userName + " sang " + receiver.getUserName() + " do xóa tài khoản.");
            } else {
                m.setDeleted(true);
                m.setUpdateBy(actor);
                m.setUpdateDate(now);
                meetings.save(m);
                purge.purge(m);
                audit(m.getId(), "DELETE", actor, m.getVersion() + 1, "Xóa cuộc họp do xóa tài khoản chủ trì " + userName + ".");
            }
        }
        // Rút khỏi các cuộc họp chưa kết thúc còn lại (họp đã kết thúc giữ lại làm lịch sử).
        for (MeetingPersonal p : personals.findByUserNameIgnoreCase(userName)) {
            boolean unfinished = meetings.findByIdAndDeletedFalse(p.getMeetingId()).map(x -> x.getStatus() <= 2).orElse(false);
            if (unfinished) personals.delete(p);
        }
        faces.deleteByUserNameIgnoreCase(userName);
        notifications.deleteByRecipientUserName(userName);
        accounts.delete(account);
    }

    private List<MeetingInfo> ownedUnfinished(String userName) {
        List<MeetingInfo> out = new ArrayList<>();
        for (MeetingPersonal p : personals.findByUserNameIgnoreCase(userName)) {
            if (p.getType() != 1) continue;
            meetings.findByIdAndDeletedFalse(p.getMeetingId()).filter(m -> m.getStatus() <= 2).ifPresent(out::add);
        }
        out.sort(Comparator.comparing(MeetingInfo::getExpectedStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private void audit(String meetingId, String action, String actor, int version, String payload) {
        MeetingAuditLog log = new MeetingAuditLog();
        log.setId(UUID.randomUUID().toString().replace("-", ""));
        log.setMeetingId(meetingId);
        log.setAction(action);
        log.setActorId(actor);
        log.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
        log.setCorrelationId(UUID.randomUUID().toString());
        log.setVersion(version);
        log.setPayloadJson(payload);
        audits.save(log);
    }
}
