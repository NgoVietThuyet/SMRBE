package vn.com.d2s.smr.service.nt.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.nt.Notification;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.repository.nt.NotificationRepository;
import vn.com.d2s.smr.service.nt.UserNotificationPublisher;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Biến domain event thành thông báo cho đúng người nhận.
 *
 * <p>Chạy ĐỒNG BỘ trong transaction nghiệp vụ (@EventListener): nghiệp vụ rollback thì
 * thông báo cũng rollback. Realtime chỉ đẩy SAU commit.</p>
 *
 * <p>Quy tắc người nhận (Plan_ThongBao.md §2, §4):</p>
 * <ul>
 *   <li>Tính từ dữ liệu quyền thật tại thời điểm sự kiện, không tin danh sách từ client.</li>
 *   <li>Không gửi cho chính người gây ra sự kiện.</li>
 *   <li>Chỉ gửi cho tài khoản tồn tại và đang hoạt động (khách mời tạm thời bị loại).</li>
 *   <li>Thông báo task chỉ gửi cho người còn xem được task đó.</li>
 * </ul>
 */
@Component
public class NotificationDispatcher {

    static final int INFO = 0;
    static final int WARNING = 1;

    private static final String[] LEVEL_LABEL = {"công việc", "đầu việc", "việc nhỏ"};
    private static final String[] STATUS_LABEL = {"Chưa bắt đầu", "Đang thực hiện", "Hoàn thành"};

    private final NotificationRepository notificationRepository;
    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final MeetingTaskRepository taskRepository;
    private final MeetingTaskShareRepository shareRepository;
    private final AdAccountRepository accountRepository;
    private final UserNotificationPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public NotificationDispatcher(
            NotificationRepository notificationRepository,
            MeetingInfoRepository meetingRepository,
            MeetingPersonalRepository personalRepository,
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            AdAccountRepository accountRepository,
            UserNotificationPublisher publisher,
            ObjectMapper objectMapper
    ) {
        this(notificationRepository, meetingRepository, personalRepository, taskRepository, shareRepository,
                accountRepository, publisher, objectMapper, Clock.systemUTC());
    }

    NotificationDispatcher(
            NotificationRepository notificationRepository,
            MeetingInfoRepository meetingRepository,
            MeetingPersonalRepository personalRepository,
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            AdAccountRepository accountRepository,
            UserNotificationPublisher publisher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.taskRepository = taskRepository;
        this.shareRepository = shareRepository;
        this.accountRepository = accountRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ── Cuộc họp ──────────────────────────────────────

    @EventListener
    public void onMeetingInvited(NotificationEvents.MeetingInvited e) {
        MeetingInfo meeting = activeMeeting(e.meetingId());
        if (meeting == null || e.userNames() == null) {
            return;
        }
        // Chỉ người hiện vẫn là thành viên mới nhận lời mời.
        Set<String> members = memberUserNames(e.meetingId());
        List<String> candidates = e.userNames().stream()
                .filter(u -> containsIgnoreCase(members, u))
                .toList();
        deliver(candidates, e.actor(), new Draft(
                "MEETING_INVITED", INFO,
                actorName(e.actor()) + " mời bạn tham gia cuộc họp",
                meeting.getName(),
                "meeting", meeting.getId(), meeting.getId(),
                meetingData(meeting),
                "MEETING_INVITED:" + meeting.getId()
        ));
    }

    @EventListener
    public void onMeetingRescheduled(NotificationEvents.MeetingRescheduled e) {
        MeetingInfo meeting = activeMeeting(e.meetingId());
        if (meeting == null) {
            return;
        }
        deliver(memberUserNames(e.meetingId()), e.actor(), new Draft(
                "MEETING_RESCHEDULED", INFO,
                "Cuộc họp đã đổi lịch",
                meeting.getName(),
                "meeting", meeting.getId(), meeting.getId(),
                meetingData(meeting),
                "MEETING_RESCHEDULED:" + meeting.getId()
        ));
    }

    @EventListener
    public void onMeetingCancelled(NotificationEvents.MeetingCancelled e) {
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(e.meetingId()).orElse(null);
        if (meeting == null || meeting.isDraft()) {
            return;
        }
        Map<String, Object> data = meetingData(meeting);
        data.put("reason", e.reason());
        deliver(memberUserNames(e.meetingId()), e.actor(), new Draft(
                "MEETING_CANCELLED", WARNING,
                "Cuộc họp đã bị hủy",
                meeting.getName(),
                "meeting", meeting.getId(), meeting.getId(),
                data,
                "MEETING_CANCELLED:" + meeting.getId()
        ));
    }

    @EventListener
    public void onMeetingStarted(NotificationEvents.MeetingStarted e) {
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(e.meetingId()).orElse(null);
        if (meeting == null) {
            return;
        }
        List<String> notJoined = personalRepository.findByMeetingId(e.meetingId()).stream()
                .filter(p -> !p.isJoined())
                .map(MeetingPersonal::getUserName)
                .toList();
        deliver(notJoined, e.actor(), new Draft(
                "MEETING_STARTED", WARNING,
                "Cuộc họp đã bắt đầu",
                meeting.getName(),
                "meeting", meeting.getId(), meeting.getId(),
                meetingData(meeting),
                "MEETING_STARTED:" + meeting.getId()
        ));
    }

    // ── Biên bản (P13) ────────────────────────────────

    @EventListener
    public void onMinutesReady(NotificationEvents.MinutesReady e) {
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(e.meetingId()).orElse(null);
        if (meeting == null) {
            return;
        }
        Map<String, Object> data = meetingData(meeting);
        data.put("version", e.version());
        deliver(e.recipients(), e.actor(), new Draft(
                "MINUTES_READY", INFO,
                "Bản nháp biên bản đã sẵn sàng",
                meeting.getName(),
                "meeting", meeting.getId(), meeting.getId(),
                data,
                "MINUTES_READY:" + meeting.getId()
        ));
    }

    @EventListener
    public void onMinutesPublished(NotificationEvents.MinutesPublished e) {
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(e.meetingId()).orElse(null);
        if (meeting == null) {
            return;
        }
        Map<String, Object> data = meetingData(meeting);
        data.put("version", e.version());
        deliver(memberUserNames(e.meetingId()), e.actor(), new Draft(
                "MINUTES_PUBLISHED", INFO,
                "Biên bản cuộc họp đã được phát hành",
                meeting.getName(),
                "meeting", meeting.getId(), meeting.getId(),
                data,
                "MINUTES_PUBLISHED:" + meeting.getId()
        ));
    }

    @EventListener
    public void onParticipantRemoved(NotificationEvents.MeetingParticipantRemoved e) {
        // Người nhận vừa mất quyền: không gắn link, không kèm dữ liệu cuộc họp.
        deliver(List.of(e.userName()), e.actor(), new Draft(
                "MEETING_REMOVED", WARNING,
                "Bạn đã được xóa khỏi cuộc họp",
                e.meetingName(),
                "none", null, e.meetingId(),
                new LinkedHashMap<>(),
                "MEETING_REMOVED:" + e.meetingId()
        ));
    }

    // ── Công việc ─────────────────────────────────────

    @EventListener
    public void onTaskAssigned(NotificationEvents.TaskAssigned e) {
        MeetingTask task = taskRepository.findById(e.taskId()).orElse(null);
        if (task == null || e.assignee() == null) {
            return;
        }
        deliver(viewers(task, List.of(e.assignee())), e.actor(), new Draft(
                "TASK_ASSIGNED", INFO,
                actorName(e.actor()) + " giao cho bạn một " + levelLabel(task),
                task.getTitle(),
                "task", task.getId(), task.getMeetingId(),
                taskData(task),
                "TASK_ASSIGNED:" + task.getId()
        ));
    }

    @EventListener
    public void onTaskStatusChanged(NotificationEvents.TaskStatusChanged e) {
        MeetingTask task = taskRepository.findById(e.taskId()).orElse(null);
        if (task == null || e.oldStatus() == e.newStatus()) {
            return;
        }
        String actorName = actorName(e.actor());
        Set<String> direct = new LinkedHashSet<>();
        addIfPresent(direct, task.getCreateBy());
        addIfPresent(direct, task.getAssigneeUserName());
        Map<String, Object> data = taskData(task);
        data.put("status", e.newStatus());
        deliver(viewers(task, direct), e.actor(), new Draft(
                "TASK_STATUS_CHANGED", INFO,
                actorName + " chuyển " + levelLabel(task) + " sang “" + statusLabel(e.newStatus()) + "”",
                task.getTitle(),
                "task", task.getId(), task.getMeetingId(),
                data,
                "TASK_STATUS_CHANGED:" + task.getId()
        ));

        // Việc con hoàn thành → báo người tạo bảng (nếu chưa nhận thông báo ở trên).
        if (e.newStatus() == 2 && task.getLevel() > 0) {
            MeetingTask board = root(task);
            if (board != null && board.getCreateBy() != null && !containsIgnoreCase(direct, board.getCreateBy())) {
                deliver(viewers(task, List.of(board.getCreateBy())), e.actor(), new Draft(
                        "TASK_COMPLETED", INFO,
                        actorName + " đã hoàn thành một " + levelLabel(task),
                        task.getTitle(),
                        "task", task.getId(), task.getMeetingId(),
                        data,
                        "TASK_COMPLETED:" + task.getId()
                ));
            }
        }
    }

    @EventListener
    public void onTaskShared(NotificationEvents.TaskShared e) {
        MeetingTask task = taskRepository.findById(e.taskId()).orElse(null);
        if (task == null) {
            return;
        }
        deliver(viewers(task, List.of(e.userName())), e.actor(), new Draft(
                "TASK_SHARED", INFO,
                actorName(e.actor()) + " đã chia sẻ một " + levelLabel(task) + " với bạn",
                task.getTitle(),
                "task", task.getId(), task.getMeetingId(),
                taskData(task),
                "TASK_SHARED:" + task.getId()
        ));
    }

    // ── Lưu + đẩy ─────────────────────────────────────

    record Draft(String type, int severity, String title, String body, String resourceType,
                 String resourceId, String meetingId, Map<String, Object> data, String dedupeKey) {
    }

    /**
     * Upsert theo (người nhận, DedupeKey): cùng sự kiện lặp lại chỉ làm mới dòng cũ
     * (đổi giờ họp 3 lần = 1 thông báo mới nhất, chưa đọc).
     */
    void deliver(Collection<String> candidates, String actor, Draft d) {
        List<String> recipients = activeAccounts(candidates.stream()
                .filter(Objects::nonNull)
                .filter(u -> !u.equalsIgnoreCase(actor))
                .toList());
        if (recipients.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String actorFullName = actorName(actor);
        String dataJson = toJson(d.data());

        List<Notification> saved = new ArrayList<>();
        for (String recipient : recipients) {
            Notification n = notificationRepository.findByRecipientUserNameAndDedupeKey(recipient, d.dedupeKey())
                    .orElseGet(() -> {
                        Notification created = new Notification();
                        created.setId(UUID.randomUUID().toString().replace("-", ""));
                        created.setRecipientUserName(recipient);
                        created.setDedupeKey(d.dedupeKey());
                        return created;
                    });
            n.setType(d.type());
            n.setSeverity(d.severity());
            n.setTitle(truncate(d.title(), 300));
            n.setBody(truncate(d.body(), 1000));
            n.setResourceType(d.resourceType());
            n.setResourceId(d.resourceId());
            n.setMeetingId(d.meetingId());
            n.setActorUserName(actor);
            n.setActorFullName(truncate(actorFullName, 300));
            n.setDataJson(dataJson);
            n.setRead(false);
            n.setReadAt(null);
            n.setCreateDate(now);
            saved.add(notificationRepository.save(n));
        }
        afterCommit(() -> push(saved));
    }

    private void push(List<Notification> saved) {
        for (Notification n : saved) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", UUID.randomUUID().toString());
            payload.put("occurredAt", n.getCreateDate().toInstant(ZoneOffset.UTC));
            payload.put("notification", NotificationMapper.toResponse(n, objectMapper));
            payload.put("unreadCount", notificationRepository.countByRecipientUserNameAndReadFalse(n.getRecipientUserName()));
            publisher.publishToUsers(List.of(n.getRecipientUserName()), "NotificationCreated", payload);
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ── Quyền / dữ liệu ───────────────────────────────

    /** Cuộc họp còn tồn tại và không phải bản nháp (bản nháp chưa gửi lời mời). */
    private MeetingInfo activeMeeting(String meetingId) {
        MeetingInfo meeting = meetingRepository.findByIdAndDeletedFalse(meetingId).orElse(null);
        if (meeting == null || meeting.isDraft() || meeting.getStatus() == 0) {
            return null;
        }
        return meeting;
    }

    private Set<String> memberUserNames(String meetingId) {
        return personalRepository.findByMeetingId(meetingId).stream()
                .map(MeetingPersonal::getUserName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Lọc những người còn XEM được task — cùng điều kiện với TaskServiceImpl.viewableTasksSpec. */
    private List<String> viewers(MeetingTask task, Collection<String> candidates) {
        return candidates.stream().filter(u -> canView(u, task)).toList();
    }

    boolean canView(String userName, MeetingTask task) {
        if (userName == null) {
            return false;
        }
        return userName.equalsIgnoreCase(task.getCreateBy())
                || task.isPublicTask()
                || userName.equalsIgnoreCase(task.getAssigneeUserName())
                || shareRepository.existsByTaskIdAndUserName(task.getId(), userName)
                || (task.getMeetingId() != null && personalRepository.existsByMeetingIdAndUserName(task.getMeetingId(), userName));
    }

    private List<String> activeAccounts(List<String> userNames) {
        if (userNames.isEmpty()) {
            return List.of();
        }
        return accountRepository.findAllById(new LinkedHashSet<>(userNames)).stream()
                .filter(AdAccount::isActive)
                .map(AdAccount::getUserName)
                .distinct()
                .toList();
    }

    private String actorName(String actor) {
        if (actor == null) {
            return "Hệ thống";
        }
        return accountRepository.findById(actor)
                .map(a -> a.getFullName() != null && !a.getFullName().isBlank() ? a.getFullName() : a.getUserName())
                .orElse(actor);
    }

    private MeetingTask root(MeetingTask task) {
        MeetingTask cur = task;
        for (int i = 0; i < 3 && cur != null && cur.getParentId() != null; i++) {
            cur = taskRepository.findById(cur.getParentId()).orElse(null);
        }
        return cur;
    }

    private Map<String, Object> meetingData(MeetingInfo meeting) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("meetingName", meeting.getName());
        data.put("startTime", meeting.getExpectedStartTime() != null ? meeting.getExpectedStartTime().toInstant(ZoneOffset.UTC) : null);
        data.put("endTime", meeting.getExpectedEndTime() != null ? meeting.getExpectedEndTime().toInstant(ZoneOffset.UTC) : null);
        return data;
    }

    /** FE cần workId (công việc gốc) để dựng link bảng / thẻ. */
    private Map<String, Object> taskData(MeetingTask task) {
        MeetingTask board = task.getLevel() == 0 ? task : root(task);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("level", task.getLevel());
        data.put("parentId", task.getParentId());
        data.put("workId", board != null ? board.getId() : task.getId());
        data.put("workTitle", board != null ? board.getTitle() : null);
        return data;
    }

    private static String levelLabel(MeetingTask task) {
        int level = task.getLevel();
        return level >= 0 && level < LEVEL_LABEL.length ? LEVEL_LABEL[level] : LEVEL_LABEL[0];
    }

    private static String statusLabel(int status) {
        return status >= 0 && status < STATUS_LABEL.length ? STATUS_LABEL[status] : "Không xác định";
    }

    private static void addIfPresent(Set<String> set, String userName) {
        if (userName != null && !userName.isBlank() && !containsIgnoreCase(set, userName)) {
            set.add(userName);
        }
    }

    private static boolean containsIgnoreCase(Collection<String> values, String value) {
        return value != null && values.stream().anyMatch(value::equalsIgnoreCase);
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
