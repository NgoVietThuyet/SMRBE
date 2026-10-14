package vn.com.d2s.smr.service.mt.impl;

import vn.com.d2s.smr.config.BusinessTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.mt.task.CreateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.TaskDetailResponse;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;
import vn.com.d2s.smr.dto.mt.task.TaskSearchRequest;
import vn.com.d2s.smr.dto.mt.task.TaskSearchResultResponse;
import vn.com.d2s.smr.dto.mt.task.TaskShareInputRequest;
import vn.com.d2s.smr.dto.mt.task.TaskShareResponse;
import vn.com.d2s.smr.dto.mt.task.TaskSummaryResponse;
import vn.com.d2s.smr.dto.mt.task.TaskVisibilityRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskRequest;
import vn.com.d2s.smr.dto.mt.task.UpdateTaskStatusRequest;
import vn.com.d2s.smr.entity.ad.AdAccount;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.service.mt.TaskService;
import vn.com.d2s.smr.service.nt.event.NotificationEvents;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskServiceImpl implements TaskService {

    private final MeetingTaskRepository taskRepository;
    private final MeetingTaskShareRepository shareRepository;
    private final MeetingInfoRepository meetingRepository;
    private final MeetingPersonalRepository personalRepository;
    private final AdAccountRepository accountRepository;
    private final Clock clock;
    /** Domain event cho thông báo; mặc định no-op để unit test không cần Spring context. */
    private ApplicationEventPublisher events = event -> { };

    @Autowired
    public TaskServiceImpl(
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            MeetingInfoRepository meetingRepository,
            MeetingPersonalRepository personalRepository,
            AdAccountRepository accountRepository
    ) {
        this(
                taskRepository,
                shareRepository,
                meetingRepository,
                personalRepository,
                accountRepository,
                Clock.systemUTC()
        );
    }

    TaskServiceImpl(
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            MeetingInfoRepository meetingRepository,
            MeetingPersonalRepository personalRepository,
            AdAccountRepository accountRepository,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.shareRepository = shareRepository;
        this.meetingRepository = meetingRepository;
        this.personalRepository = personalRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** P12: quyền theo bảng + realtime; null trong unit test cũ → giữ quy tắc cũ (quyền theo từng việc). */
    private TaskAccess access;
    private vn.com.d2s.smr.service.mt.TaskBoardService board;

    @Autowired(required = false)
    void setAccess(TaskAccess access) {
        this.access = access;
    }

    @Autowired(required = false)
    void setBoard(vn.com.d2s.smr.service.mt.TaskBoardService board) {
        this.board = board;
    }

    private boolean canEdit(MeetingTask task, String userName) {
        if (access != null) {
            return access.rights(task, userName).edit();
        }
        return task.getCreateBy().equalsIgnoreCase(userName)
                || shareRepository.existsByTaskIdAndUserNameAndPermission(task.getId(), userName, 1);
    }

    private void changed(MeetingTask task, String action, String userName) {
        if (access != null) {
            access.notifyChanged(access.root(task), task.getId(), action, userName);
        }
    }

    private static void validateColor(String color) {
        if (color != null && !color.isBlank() && !color.trim().matches("^#[0-9a-fA-F]{6}$")) {
            throw new IllegalArgumentException("Màu không hợp lệ (dạng #RRGGBB).");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TaskSearchResultResponse searchTasks(String userName, TaskSearchRequest request) {
        int page = Math.max(1, request.page());
        int pageSize = Math.max(1, Math.min(100, request.pageSize()));

        Specification<MeetingTask> baseSpec = viewableTasksSpec(userName);

        if (request.keyword() != null && !request.keyword().isBlank()) {
            String kw = request.keyword().trim().toLowerCase();
            Specification<MeetingTask> kwSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), "%" + kw + "%"),
                    cb.like(cb.lower(root.get("description")), "%" + kw + "%")
            );
            baseSpec = baseSpec.and(kwSpec);
        }

        if (request.meetingId() != null && !request.meetingId().isBlank()) {
            if ("none".equalsIgnoreCase(request.meetingId().trim())) {
                baseSpec = baseSpec.and((root, query, cb) -> cb.isNull(root.get("meetingId")));
            } else {
                String mId = request.meetingId().trim();
                baseSpec = baseSpec.and((root, query, cb) -> cb.equal(root.get("meetingId"), mId));
            }
        }

        if (request.assignee() != null && !request.assignee().isBlank()) {
            if ("none".equalsIgnoreCase(request.assignee().trim())) {
                baseSpec = baseSpec.and((root, query, cb) -> cb.isNull(root.get("assigneeUserName")));
            } else {
                String ass = request.assignee().trim();
                baseSpec = baseSpec.and((root, query, cb) -> cb.equal(root.get("assigneeUserName"), ass));
            }
        }

        if (request.status() != null) {
            baseSpec = baseSpec.and((root, query, cb) -> cb.equal(root.get("status"), request.status()));
        }

        if (request.priority() != null) {
            baseSpec = baseSpec.and((root, query, cb) -> cb.equal(root.get("priority"), request.priority()));
        }

        if (request.level() != null) {
            baseSpec = baseSpec.and((root, query, cb) -> cb.equal(root.get("level"), request.level()));
        }

        if (request.parentId() != null && !request.parentId().isBlank()) {
            String pId = request.parentId().trim();
            baseSpec = baseSpec.and((root, query, cb) -> cb.equal(root.get("parentId"), pId));
        }

        // P12: nhóm ở sidebar (mine · assigned · shared · public) và ẩn việc đã lưu trữ.
        Specification<MeetingTask> groupSpec = TaskSpecs.group(request.group(), userName);
        if (groupSpec != null) {
            baseSpec = baseSpec.and(groupSpec);
        }
        if (!Boolean.TRUE.equals(request.includeArchived())) {
            baseSpec = baseSpec.and(TaskSpecs.notArchived());
        }

        // DueDate lưu theo UTC → "hôm nay" = [00:00, 24:00) giờ Việt Nam quy đổi sang UTC.
        LocalDate today = BusinessTime.today(clock);
        LocalDateTime todayStart = BusinessTime.startOfDayUtc(today);
        LocalDateTime tomorrowStart = BusinessTime.startOfDayUtc(today.plusDays(1));

        long totalCount = taskRepository.count(baseSpec);
        long mineCount = taskRepository.count(baseSpec.and(mineSpec(userName)));
        long overdueCount = taskRepository.count(baseSpec.and(overdueSpec(todayStart)));
        long todayCount = taskRepository.count(baseSpec.and(todaySpec(todayStart, tomorrowStart)));

        TaskSummaryResponse summary = new TaskSummaryResponse(
                (int) totalCount,
                (int) mineCount,
                (int) overdueCount,
                (int) todayCount
        );

        Specification<MeetingTask> filteredSpec = baseSpec;
        if (request.shortcut() != null) {
            switch (request.shortcut().trim().toLowerCase()) {
                case "mine" -> filteredSpec = filteredSpec.and(mineSpec(userName));
                case "overdue" -> filteredSpec = filteredSpec.and(overdueSpec(todayStart));
                case "today" -> filteredSpec = filteredSpec.and(todaySpec(todayStart, tomorrowStart));
                default -> { }
            }
        }

        Page<MeetingTask> taskPage = taskRepository.findAll(filteredSpec, PageRequest.of(page - 1, pageSize));
        List<MeetingTask> tasks = taskPage.getContent();

        List<TaskListItemResponse> items = tasks.stream()
                .map(t -> toListItemResponse(t, userName))
                .sorted(Comparator.comparing(TaskListItemResponse::meetingStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TaskListItemResponse::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskListItemResponse::createDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new TaskSearchResultResponse(
                items,
                taskPage.getTotalElements(),
                taskPage.getTotalPages(),
                page,
                pageSize,
                summary
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getTask(String userName, String taskId) {
        MeetingTask task = findViewable(taskId, userName);

        return toDetailResponse(task, userName);
    }

    @Override
    @Transactional
    public TaskListItemResponse createTask(String creatorUserName, CreateTaskRequest request) {
        String title = request.title() != null ? request.title().trim() : "";
        if (title.length() < 1 || title.length() > 300) {
            throw new IllegalArgumentException("Tên công việc phải có từ 1 đến 300 ký tự.");
        }
        validateEnums(request.status(), request.priority());

        MeetingTask parent = null;
        int level = request.level();
        String parentId = (request.parentId() != null && !request.parentId().isBlank()) ? request.parentId().trim() : null;

        if (parentId != null) {
            parent = taskRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc cha."));

            boolean canViewParent = taskRepository.exists(viewableTasksSpec(creatorUserName).and((root, query, cb) -> cb.equal(root.get("id"), parentId)));
            if (!canViewParent) {
                throw new SecurityException("Bạn không có quyền tạo công việc con trong công việc này.");
            }
            // P12: thêm cột / thẻ vào bảng cần quyền Sửa trên bảng.
            if (access != null && !access.rights(parent, creatorUserName).edit()) {
                throw new SecurityException("Bạn chỉ có quyền xem công việc này.");
            }
            if (parent.getLevel() >= 2) {
                throw new IllegalStateException("Công việc đã đạt độ sâu tối đa (3 cấp).");
            }
            level = parent.getLevel() + 1;
        } else {
            level = 0;
        }

        if (level < 0 || level > 2) {
            throw new IllegalArgumentException("Cấp công việc không hợp lệ (0-2).");
        }

        String meetingId = null;
        if (request.meetingId() != null && !request.meetingId().isBlank()) {
            meetingId = request.meetingId().trim();
            final String mId = meetingId;
            meetingRepository.findByIdAndDeletedFalse(mId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc họp."));
            if (!personalRepository.existsByMeetingIdAndUserName(mId, creatorUserName)) {
                throw new SecurityException("Bạn không thuộc cuộc họp này nên không thể tạo công việc cho cuộc họp.");
            }
        } else if (parent != null && parent.getMeetingId() != null) {
            meetingId = parent.getMeetingId();
        }

        String assignee = resolveAssignee(request.assigneeUserName());
        validateColor(request.color());
        if (request.startDate() != null && request.dueDate() != null && request.startDate().isAfter(request.dueDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu phải trước hạn hoàn thành.");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        MeetingTask task = new MeetingTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setMeetingId(meetingId);
        task.setParentId(parentId);
        task.setLevel(level);
        task.setTitle(title);
        task.setDescription(request.description() != null && !request.description().isBlank() ? request.description().trim() : null);
        task.setAssigneeUserName(assignee);
        task.setDueDate(request.dueDate());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setPublicTask(request.isPublic());
        task.setSourceRef(request.sourceRef() != null && !request.sourceRef().isBlank() ? request.sourceRef().trim() : null);
        task.setStartDate(request.startDate());
        task.setColor(request.color() != null && !request.color().isBlank() ? request.color().trim() : null);
        task.setSortOrder(parentId != null ? taskRepository.maxSortOrder(parentId) + 1 : 0);
        task.setCreateBy(creatorUserName);
        task.setCreateDate(now);
        task.setUpdateBy(creatorUserName);
        task.setUpdateDate(now);

        taskRepository.save(task);

        if (task.getAssigneeUserName() != null) {
            events.publishEvent(new NotificationEvents.TaskAssigned(task.getId(), creatorUserName, task.getAssigneeUserName()));
        }
        changed(task, "created", creatorUserName);

        return toListItemResponse(task, creatorUserName);
    }

    @Override
    @Transactional
    public TaskListItemResponse updateTask(String userName, String taskId, UpdateTaskRequest request) {
        String title = request.title() != null ? request.title().trim() : "";
        if (title.length() < 1 || title.length() > 300) {
            throw new IllegalArgumentException("Tên công việc phải có từ 1 đến 300 ký tự.");
        }
        validateEnums(request.status(), request.priority());

        MeetingTask task = findViewable(taskId, userName);

        if (!canEdit(task, userName)) {
            throw new SecurityException("Bạn chỉ có quyền xem công việc này.");
        }
        validateColor(request.color());

        if (request.parentId() != null) {
            String newParentId = request.parentId().isBlank() ? null : request.parentId().trim();
            if (!Objects.equals(newParentId, task.getParentId())) {
                if (taskId.equals(newParentId)) {
                    throw new IllegalStateException("Không thể đặt công việc làm cha của chính nó.");
                }
                if (newParentId != null) {
                    MeetingTask parent = taskRepository.findById(newParentId)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc cha."));
                    if (parent.getLevel() >= 2) {
                        throw new IllegalStateException("Công việc cha đã ở cấp sâu nhất.");
                    }
                    if (isDescendant(newParentId, taskId)) {
                        throw new IllegalStateException("Không thể di chuyển tạo vòng lặp.");
                    }
                    task.setParentId(newParentId);
                    task.setLevel(parent.getLevel() + 1);
                    int maxDescDepth = getMaxDescendantDepth(taskId);
                    if (task.getLevel() + maxDescDepth > 2) {
                        throw new IllegalStateException("Di chuyển sẽ vượt quá độ sâu 3 cấp.");
                    }
                } else {
                    int maxDescDepth = getMaxDescendantDepth(taskId);
                    if (maxDescDepth > 2) {
                        throw new IllegalStateException("Cây con quá sâu khi đưa về gốc.");
                    }
                    task.setParentId(null);
                    task.setLevel(0);
                }
            }
        }

        if (request.level() != null && request.parentId() == null && request.level() != task.getLevel()) {
            boolean hasChildren = taskRepository.existsByParentId(taskId);
            if (hasChildren) {
                throw new IllegalStateException("Không thể đổi cấp khi còn công việc con.");
            }
            if (request.level() < 0 || request.level() > 2) {
                throw new IllegalArgumentException("Cấp không hợp lệ.");
            }
            task.setLevel(request.level());
        }

        String oldAssignee = task.getAssigneeUserName();
        int oldStatus = task.getStatus();

        task.setTitle(title);
        task.setDescription(request.description() != null && !request.description().isBlank() ? request.description().trim() : null);
        task.setAssigneeUserName(resolveAssignee(request.assigneeUserName()));
        task.setDueDate(request.dueDate());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        if (request.sourceRef() != null && !request.sourceRef().isBlank()) {
            task.setSourceRef(request.sourceRef().trim());
        }
        // P12: null = giữ nguyên; clearDates = xoá cả ngày bắt đầu.
        if (Boolean.TRUE.equals(request.clearDates())) {
            task.setStartDate(null);
        } else if (request.startDate() != null) {
            task.setStartDate(request.startDate());
        }
        if (task.getStartDate() != null && task.getDueDate() != null && task.getStartDate().isAfter(task.getDueDate())) {
            throw new IllegalArgumentException("Ngày bắt đầu phải trước hạn hoàn thành.");
        }
        if (request.color() != null) {
            task.setColor(request.color().isBlank() ? null : request.color().trim());
        }
        if (request.isArchived() != null) {
            task.setArchived(request.isArchived());
        }
        task.setUpdateBy(userName);
        task.setUpdateDate(LocalDateTime.now(clock));

        taskRepository.save(task);

        propagateLevel(task.getId(), task.getLevel(), userName);

        String newAssignee = task.getAssigneeUserName();
        if (newAssignee != null && !newAssignee.equalsIgnoreCase(oldAssignee)) {
            events.publishEvent(new NotificationEvents.TaskAssigned(task.getId(), userName, newAssignee));
        }
        if (oldStatus != task.getStatus()) {
            events.publishEvent(new NotificationEvents.TaskStatusChanged(task.getId(), userName, oldStatus, task.getStatus()));
        }
        changed(task, "updated", userName);

        return toListItemResponse(task, userName);
    }

    private MeetingTask findViewable(String taskId, String userName) {
        if (access != null) {
            MeetingTask task = access.find(taskId);
            if (!access.rights(task, userName).view()) {
                throw new IllegalArgumentException("Không tìm thấy công việc.");
            }
            return task;
        }
        return taskRepository.findOne(viewableTasksSpec(userName).and((root, query, cb) -> cb.equal(root.get("id"), taskId)))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));
    }

    @Override
    @Transactional
    public TaskListItemResponse deleteTask(String userName, String taskId) {
        MeetingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));

        if (access == null) {
            if (!task.getCreateBy().equalsIgnoreCase(userName)) {
                throw new SecurityException("Chỉ người tạo công việc mới được xóa.");
            }
            if (taskRepository.existsByParentId(taskId)) {
                throw new IllegalStateException("Không thể xóa công việc còn chứa công việc con. Hãy xóa con trước.");
            }
            TaskListItemResponse snapshot = toListItemResponse(task, userName);
            shareRepository.deleteByTaskId(taskId);
            taskRepository.delete(task);
            return snapshot;
        }

        // P12: bảng (cấp 0) chỉ người tạo xoá; cột / thẻ: người có quyền Sửa bảng. Xoá kèm toàn bộ cây con.
        TaskAccess.Rights rights = access.rights(task, userName);
        boolean allowed = task.getLevel() == 0 ? rights.owner() || task.getCreateBy().equalsIgnoreCase(userName) : rights.edit();
        if (!allowed) {
            throw new SecurityException(task.getLevel() == 0
                    ? "Chỉ người tạo công việc mới được xóa." : "Bạn chỉ có quyền xem công việc này.");
        }
        MeetingTask root = rights.root();
        TaskListItemResponse snapshot = toListItemResponse(task, userName);
        List<MeetingTask> subtree = new ArrayList<>();
        List<MeetingTask> level = List.of(task);
        while (!level.isEmpty()) {
            subtree.addAll(level);
            level = taskRepository.findByParentIdIn(level.stream().map(MeetingTask::getId).toList());
        }
        List<String> ids = subtree.stream().map(MeetingTask::getId).toList();
        if (board != null) {
            board.purge(ids, task.getLevel() == 0 ? task.getId() : null);
        }
        ids.forEach(shareRepository::deleteByTaskId);
        // Xoá từ lá lên gốc.
        for (int i = subtree.size() - 1; i >= 0; i--) {
            taskRepository.delete(subtree.get(i));
        }
        if (task.getLevel() != 0) {
            access.notifyChanged(root, taskId, "deleted", userName);
        }
        return snapshot;
    }

    @Override
    @Transactional
    public TaskListItemResponse updateTaskStatus(String userName, String taskId, UpdateTaskStatusRequest request) {
        if (request.status() < 0 || request.status() > 2) {
            throw new IllegalArgumentException("Trạng thái công việc không hợp lệ.");
        }

        MeetingTask task = findViewable(taskId, userName);

        // P12: người thực hiện cũng được đổi trạng thái việc của mình.
        boolean allowed = canEdit(task, userName)
                || (access != null && task.getAssigneeUserName() != null && task.getAssigneeUserName().equalsIgnoreCase(userName));
        if (!allowed) {
            throw new SecurityException("Bạn chỉ có quyền xem công việc này.");
        }

        int oldStatus = task.getStatus();
        task.setStatus(request.status());
        task.setUpdateBy(userName);
        task.setUpdateDate(LocalDateTime.now(clock));

        taskRepository.save(task);

        if (oldStatus != task.getStatus()) {
            events.publishEvent(new NotificationEvents.TaskStatusChanged(task.getId(), userName, oldStatus, task.getStatus()));
            changed(task, "status", userName);
        }

        return toListItemResponse(task, userName);
    }

    @Override
    @Transactional
    public void setVisibility(String userName, String taskId, TaskVisibilityRequest request) {
        MeetingTask task = ensureCreator(taskId, userName);
        task.setPublicTask(request.isPublic());
        task.setUpdateBy(userName);
        task.setUpdateDate(LocalDateTime.now(clock));
        taskRepository.save(task);
        changed(task, "visibility", userName);
    }

    @Override
    @Transactional
    public TaskDetailResponse addShare(String userName, String taskId, TaskShareInputRequest request) {
        MeetingTask task = ensureCreator(taskId, userName);

        if (request.userName() == null || request.userName().isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn người được chia sẻ.");
        }
        if (request.permission() < 0 || request.permission() > 1) {
            throw new IllegalArgumentException("Quyền chia sẻ không hợp lệ.");
        }

        String target = request.userName().trim();
        if (target.equalsIgnoreCase(userName)) {
            throw new IllegalArgumentException("Không thể chia sẻ công việc cho chính người tạo.");
        }

        AdAccount account = accountRepository.findById(target)
                .filter(AdAccount::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + target + "."));

        if (shareRepository.existsByTaskIdAndUserName(taskId, account.getUserName())) {
            throw new IllegalStateException("Người này đã được chia sẻ công việc này.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        MeetingTaskShare share = new MeetingTaskShare();
        share.setId(UUID.randomUUID().toString().replace("-", ""));
        share.setTaskId(taskId);
        share.setUserName(account.getUserName());
        share.setPermission(request.permission());
        share.setCreateBy(userName);
        share.setCreateDate(now);
        share.setUpdateBy(userName);
        share.setUpdateDate(now);

        shareRepository.save(share);
        events.publishEvent(new NotificationEvents.TaskShared(taskId, userName, account.getUserName()));

        task.setUpdateBy(userName);
        task.setUpdateDate(now);
        taskRepository.save(task);

        return toDetailResponse(task, userName);
    }

    @Override
    @Transactional
    public void removeShare(String userName, String taskId, String targetUserName) {
        ensureCreator(taskId, userName);

        MeetingTaskShare share = shareRepository.findByTaskIdAndUserName(taskId, targetUserName)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người được chia sẻ."));

        shareRepository.delete(share);
    }

    private MeetingTask ensureCreator(String taskId, String userName) {
        MeetingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));
        if (!task.getCreateBy().equalsIgnoreCase(userName)) {
            throw new SecurityException("Chỉ người tạo công việc mới được thay đổi thiết lập chia sẻ.");
        }
        return task;
    }

    private TaskListItemResponse toListItemResponse(MeetingTask task, String userName) {
        String meetingName = null;
        LocalDateTime meetingStartTime = null;
        if (task.getMeetingId() != null) {
            Optional<MeetingInfo> mOpt = meetingRepository.findById(task.getMeetingId());
            if (mOpt.isPresent()) {
                meetingName = mOpt.get().getName();
                meetingStartTime = mOpt.get().getExpectedStartTime();
            }
        }

        String assigneeFullName = null;
        if (task.getAssigneeUserName() != null) {
            assigneeFullName = accountRepository.findById(task.getAssigneeUserName())
                    .map(AdAccount::getFullName)
                    .orElse(task.getAssigneeUserName());
        }

        int childrenCount = taskRepository.countByParentId(task.getId());
        int completedChildrenCount = taskRepository.countByParentIdAndStatus(task.getId(), 2);
        int progress = task.getStatus() == 2 ? 100 : (task.getStatus() == 1 ? 50 : 0);
        // P12: công việc cấp 0 — tiến độ = số việc nhỏ đã xong / tổng việc nhỏ trong bảng.
        int cardCount = 0;
        int cardDone = 0;
        if (task.getLevel() == 0) {
            cardCount = taskRepository.countCards(task.getId());
            cardDone = taskRepository.countDoneCards(task.getId());
            if (cardCount > 0) {
                progress = Math.round(cardDone * 100f / cardCount);
            }
        }

        boolean isCreator = task.getCreateBy().equalsIgnoreCase(userName);
        boolean canEdit = access != null ? canEdit(task, userName)
                : isCreator || shareRepository.existsByTaskIdAndUserNameAndPermission(task.getId(), userName, 1);

        return new TaskListItemResponse(
                task.getId(),
                task.getMeetingId(),
                meetingName,
                meetingStartTime,
                task.getParentId(),
                task.getLevel(),
                childrenCount,
                completedChildrenCount,
                progress,
                task.getTitle(),
                task.getDescription(),
                task.getAssigneeUserName(),
                assigneeFullName,
                task.getDueDate(),
                task.getStatus(),
                task.getPriority(),
                task.isPublicTask(),
                isCreator,
                canEdit,
                task.getCreateBy(),
                task.getCreateDate(),
                task.getUpdateDate(),
                task.getSourceRef(),
                task.getStartDate(),
                task.getSortOrder(),
                task.getColor(),
                task.isArchived(),
                cardCount,
                cardDone
        );
    }

    private TaskDetailResponse toDetailResponse(MeetingTask task, String userName) {
        TaskListItemResponse item = toListItemResponse(task, userName);

        List<TaskShareResponse> shares = new ArrayList<>();
        if (task.getCreateBy().equalsIgnoreCase(userName)) {
            List<MeetingTaskShare> taskShares = shareRepository.findByTaskId(task.getId());
            Map<String, String> userFullNames = accountRepository.findAllById(
                    taskShares.stream().map(MeetingTaskShare::getUserName).toList()
            ).stream().collect(Collectors.toMap(AdAccount::getUserName, AdAccount::getFullName, (a, b) -> a));

            shares = taskShares.stream()
                    .map(s -> new TaskShareResponse(
                            s.getUserName(),
                            userFullNames.getOrDefault(s.getUserName(), s.getUserName()),
                            s.getPermission()
                    ))
                    .toList();
        }

        List<MeetingTask> childTasks = taskRepository.findByParentId(task.getId());
        List<TaskListItemResponse> children = childTasks.stream()
                .map(c -> toListItemResponse(c, userName))
                .sorted(Comparator.comparing(TaskListItemResponse::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskListItemResponse::createDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new TaskDetailResponse(
                item.id(),
                item.meetingId(),
                item.meetingName(),
                item.meetingStartTime(),
                item.parentId(),
                item.level(),
                item.childrenCount(),
                item.completedChildrenCount(),
                item.progress(),
                item.title(),
                item.description(),
                item.assigneeUserName(),
                item.assigneeFullName(),
                item.dueDate(),
                item.status(),
                item.priority(),
                item.isPublic(),
                item.isCreator(),
                item.canEdit(),
                item.createBy(),
                item.createDate(),
                item.updateDate(),
                item.sourceRef(),
                shares,
                children
        );
    }

    private Specification<MeetingTask> viewableTasksSpec(String userName) {
        return (root, query, cb) -> {
            Predicate pCreateBy = cb.equal(root.get("createBy"), userName);
            Predicate pPublic = cb.isTrue(root.get("publicTask"));
            Predicate pAssignee = cb.equal(root.get("assigneeUserName"), userName);

            Subquery<String> shareSubquery = query.subquery(String.class);
            Root<MeetingTaskShare> shareRoot = shareSubquery.from(MeetingTaskShare.class);
            shareSubquery.select(shareRoot.get("taskId"))
                    .where(cb.equal(shareRoot.get("userName"), userName));
            Predicate pShare = root.get("id").in(shareSubquery);

            Subquery<String> personalSubquery = query.subquery(String.class);
            Root<MeetingPersonal> personalRoot = personalSubquery.from(MeetingPersonal.class);
            personalSubquery.select(personalRoot.get("meetingId"))
                    .where(cb.equal(personalRoot.get("userName"), userName));
            Predicate pMeetingPersonal = root.get("meetingId").in(personalSubquery);

            return cb.or(pCreateBy, pPublic, pAssignee, pShare, pMeetingPersonal);
        };
    }

    private Specification<MeetingTask> mineSpec(String userName) {
        return (root, query, cb) -> {
            Predicate pCreateBy = cb.equal(root.get("createBy"), userName);
            Predicate pAssignee = cb.equal(root.get("assigneeUserName"), userName);

            Subquery<String> shareSubquery = query.subquery(String.class);
            Root<MeetingTaskShare> shareRoot = shareSubquery.from(MeetingTaskShare.class);
            shareSubquery.select(shareRoot.get("taskId"))
                    .where(cb.equal(shareRoot.get("userName"), userName));
            Predicate pShare = root.get("id").in(shareSubquery);

            return cb.or(pCreateBy, pAssignee, pShare);
        };
    }

    private Specification<MeetingTask> overdueSpec(LocalDateTime todayStart) {
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueDate")),
                cb.lessThan(root.get("dueDate"), todayStart),
                cb.notEqual(root.get("status"), 2)
        );
    }

    private Specification<MeetingTask> todaySpec(LocalDateTime todayStart, LocalDateTime tomorrowStart) {
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueDate")),
                cb.greaterThanOrEqualTo(root.get("dueDate"), todayStart),
                cb.lessThan(root.get("dueDate"), tomorrowStart),
                cb.notEqual(root.get("status"), 2)
        );
    }

    private static void validateEnums(int status, int priority) {
        if (status < 0 || status > 2) {
            throw new IllegalArgumentException("Trạng thái công việc không hợp lệ.");
        }
        if (priority < 0 || priority > 2) {
            throw new IllegalArgumentException("Mức ưu tiên không hợp lệ.");
        }
    }

    private String resolveAssignee(String userName) {
        if (userName == null || userName.isBlank()) {
            return null;
        }
        String name = userName.trim();
        AdAccount account = accountRepository.findById(name)
                .filter(AdAccount::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người phụ trách: " + name + "."));
        return account.getUserName();
    }

    private boolean isDescendant(String candidateParentId, String taskId) {
        String cur = candidateParentId;
        for (int i = 0; i < 10; i++) {
            Optional<MeetingTask> pOpt = taskRepository.findById(cur);
            if (pOpt.isEmpty() || pOpt.get().getParentId() == null) {
                return false;
            }
            if (pOpt.get().getParentId().equalsIgnoreCase(taskId)) {
                return true;
            }
            cur = pOpt.get().getParentId();
        }
        return false;
    }

    private int getMaxDescendantDepth(String taskId) {
        List<MeetingTask> children = taskRepository.findByParentId(taskId);
        if (children.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (MeetingTask c : children) {
            int d = getMaxDescendantDepth(c.getId());
            max = Math.max(max, 1 + d);
        }
        return max;
    }

    private void propagateLevel(String parentId, int parentLevel, String userName) {
        List<MeetingTask> children = taskRepository.findByParentId(parentId);
        for (MeetingTask c : children) {
            c.setLevel(parentLevel + 1);
            c.setUpdateBy(userName);
            c.setUpdateDate(LocalDateTime.now(clock));
            taskRepository.save(c);
            propagateLevel(c.getId(), c.getLevel(), userName);
        }
    }
}
