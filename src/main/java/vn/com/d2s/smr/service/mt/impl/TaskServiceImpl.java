package vn.com.d2s.smr.service.mt.impl;

import org.springframework.beans.factory.annotation.Autowired;
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

        LocalDate today = LocalDate.now(clock);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

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
        MeetingTask task = taskRepository.findOne(viewableTasksSpec(userName).and((root, query, cb) -> cb.equal(root.get("id"), taskId)))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));

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
        task.setCreateBy(creatorUserName);
        task.setCreateDate(now);
        task.setUpdateBy(creatorUserName);
        task.setUpdateDate(now);

        taskRepository.save(task);

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

        MeetingTask task = taskRepository.findOne(viewableTasksSpec(userName).and((root, query, cb) -> cb.equal(root.get("id"), taskId)))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));

        boolean canEdit = task.getCreateBy().equalsIgnoreCase(userName)
                || shareRepository.existsByTaskIdAndUserNameAndPermission(taskId, userName, 1);
        if (!canEdit) {
            throw new SecurityException("Bạn chỉ có quyền xem công việc này.");
        }

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

        task.setTitle(title);
        task.setDescription(request.description() != null && !request.description().isBlank() ? request.description().trim() : null);
        task.setAssigneeUserName(resolveAssignee(request.assigneeUserName()));
        task.setDueDate(request.dueDate());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        if (request.sourceRef() != null && !request.sourceRef().isBlank()) {
            task.setSourceRef(request.sourceRef().trim());
        }
        task.setUpdateBy(userName);
        task.setUpdateDate(LocalDateTime.now(clock));

        taskRepository.save(task);

        propagateLevel(task.getId(), task.getLevel(), userName);

        return toListItemResponse(task, userName);
    }

    @Override
    @Transactional
    public TaskListItemResponse deleteTask(String userName, String taskId) {
        MeetingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));

        if (!task.getCreateBy().equalsIgnoreCase(userName)) {
            throw new SecurityException("Chỉ người tạo công việc mới được xóa.");
        }

        boolean hasChildren = taskRepository.existsByParentId(taskId);
        if (hasChildren) {
            throw new IllegalStateException("Không thể xóa công việc còn chứa công việc con. Hãy xóa con trước.");
        }

        TaskListItemResponse snapshot = toListItemResponse(task, userName);
        shareRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);

        return snapshot;
    }

    @Override
    @Transactional
    public TaskListItemResponse updateTaskStatus(String userName, String taskId, UpdateTaskStatusRequest request) {
        if (request.status() < 0 || request.status() > 2) {
            throw new IllegalArgumentException("Trạng thái công việc không hợp lệ.");
        }

        MeetingTask task = taskRepository.findOne(viewableTasksSpec(userName).and((root, query, cb) -> cb.equal(root.get("id"), taskId)))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));

        boolean canEdit = task.getCreateBy().equalsIgnoreCase(userName)
                || shareRepository.existsByTaskIdAndUserNameAndPermission(taskId, userName, 1);
        if (!canEdit) {
            throw new SecurityException("Bạn chỉ có quyền xem công việc này.");
        }

        task.setStatus(request.status());
        task.setUpdateBy(userName);
        task.setUpdateDate(LocalDateTime.now(clock));

        taskRepository.save(task);

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

        boolean isCreator = task.getCreateBy().equalsIgnoreCase(userName);
        boolean canEdit = isCreator || shareRepository.existsByTaskIdAndUserNameAndPermission(task.getId(), userName, 1);

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
                task.getSourceRef()
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
