package vn.com.d2s.smr.service.mt.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.config.BusinessTime;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;
import vn.com.d2s.smr.dto.mt.task.board.BoardCardResponse;
import vn.com.d2s.smr.dto.mt.task.board.BoardListResponse;
import vn.com.d2s.smr.dto.mt.task.board.BoardMemberResponse;
import vn.com.d2s.smr.dto.mt.task.board.BoardResponse;
import vn.com.d2s.smr.dto.mt.task.board.CardDetailResponse;
import vn.com.d2s.smr.dto.mt.task.board.CardLabelsRequest;
import vn.com.d2s.smr.dto.mt.task.board.ChecklistItemRequest;
import vn.com.d2s.smr.dto.mt.task.board.ChecklistItemResponse;
import vn.com.d2s.smr.dto.mt.task.board.CommentRequest;
import vn.com.d2s.smr.dto.mt.task.board.CommentResponse;
import vn.com.d2s.smr.dto.mt.task.board.LabelRequest;
import vn.com.d2s.smr.dto.mt.task.board.LabelResponse;
import vn.com.d2s.smr.dto.mt.task.board.MoveTaskRequest;
import vn.com.d2s.smr.dto.mt.task.board.TaskFileResponse;
import vn.com.d2s.smr.dto.mt.task.board.TaskGroupsResponse;
import vn.com.d2s.smr.entity.cf.CmFile;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.entity.mt.MtTaskCardLabel;
import vn.com.d2s.smr.entity.mt.MtTaskChecklistItem;
import vn.com.d2s.smr.entity.mt.MtTaskComment;
import vn.com.d2s.smr.entity.mt.MtTaskLabel;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.repository.mt.MtTaskCardLabelRepository;
import vn.com.d2s.smr.repository.mt.MtTaskChecklistItemRepository;
import vn.com.d2s.smr.repository.mt.MtTaskCommentRepository;
import vn.com.d2s.smr.repository.mt.MtTaskLabelRepository;
import vn.com.d2s.smr.service.cf.ObjectStorageService;
import vn.com.d2s.smr.service.mt.TaskBoardService;
import vn.com.d2s.smr.service.sy.SettingService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TaskBoardServiceImpl implements TaskBoardService {

    /** CmFiles.Type của file đính kèm công việc. */
    static final int FILE_TYPE_TASK = 3;
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".csv", ".png", ".jpg", ".jpeg", ".zip");
    private static final int DEFAULT_MAX_MB = 200;

    private final MeetingTaskRepository taskRepository;
    private final MeetingTaskShareRepository shareRepository;
    private final MeetingInfoRepository meetingRepository;
    private final AdAccountRepository accountRepository;
    private final MtTaskLabelRepository labelRepository;
    private final MtTaskCardLabelRepository cardLabelRepository;
    private final MtTaskChecklistItemRepository checklistRepository;
    private final MtTaskCommentRepository commentRepository;
    private final CmFileRepository fileRepository;
    private final TaskAccess access;
    private final Clock clock;
    private ObjectStorageService storage;
    private SettingService settings;

    @Autowired
    public TaskBoardServiceImpl(
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            MeetingInfoRepository meetingRepository,
            AdAccountRepository accountRepository,
            MtTaskLabelRepository labelRepository,
            MtTaskCardLabelRepository cardLabelRepository,
            MtTaskChecklistItemRepository checklistRepository,
            MtTaskCommentRepository commentRepository,
            CmFileRepository fileRepository,
            TaskAccess access
    ) {
        this(taskRepository, shareRepository, meetingRepository, accountRepository, labelRepository, cardLabelRepository,
                checklistRepository, commentRepository, fileRepository, access, Clock.systemUTC());
    }

    TaskBoardServiceImpl(
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            MeetingInfoRepository meetingRepository,
            AdAccountRepository accountRepository,
            MtTaskLabelRepository labelRepository,
            MtTaskCardLabelRepository cardLabelRepository,
            MtTaskChecklistItemRepository checklistRepository,
            MtTaskCommentRepository commentRepository,
            CmFileRepository fileRepository,
            TaskAccess access,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.shareRepository = shareRepository;
        this.meetingRepository = meetingRepository;
        this.accountRepository = accountRepository;
        this.labelRepository = labelRepository;
        this.cardLabelRepository = cardLabelRepository;
        this.checklistRepository = checklistRepository;
        this.commentRepository = commentRepository;
        this.fileRepository = fileRepository;
        this.access = access;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setStorage(ObjectStorageService storage) {
        this.storage = storage;
    }

    @Autowired(required = false)
    void setSettings(SettingService settings) {
        this.settings = settings;
    }

    // ===================== T2: sidebar =====================

    @Override
    @Transactional(readOnly = true)
    public TaskGroupsResponse groups(String userName) {
        LocalDate today = BusinessTime.today(clock);
        LocalDateTime todayStart = BusinessTime.startOfDayUtc(today);
        LocalDateTime tomorrowStart = BusinessTime.startOfDayUtc(today.plusDays(1));

        int dueToday = (int) taskRepository.count(TaskSpecs.myDue(userName, todayStart, tomorrowStart));
        int overdue = (int) taskRepository.count(TaskSpecs.myDue(userName, null, todayStart));
        var boards = TaskSpecs.viewable(userName).and(TaskSpecs.level(0));
        int mine = (int) taskRepository.count(boards.and(TaskSpecs.group("mine", userName)));
        int assigned = (int) taskRepository.count(boards.and(TaskSpecs.group("assigned", userName)));
        int shared = (int) taskRepository.count(boards.and(TaskSpecs.group("shared", userName)));
        int pub = (int) taskRepository.count(boards.and(TaskSpecs.group("public", userName)));

        // Công việc cấp 0 gắn cuộc họp → nhóm theo cuộc họp.
        Map<String, Integer> perMeeting = new LinkedHashMap<>();
        taskRepository.findAll(boards.and((root, q, cb) -> cb.isNotNull(root.get("meetingId"))))
                .forEach(t -> perMeeting.merge(t.getMeetingId(), 1, Integer::sum));
        Map<String, MeetingInfo> meetings = meetingRepository.findAllById(perMeeting.keySet()).stream()
                .filter(m -> !m.isDeleted())
                .collect(Collectors.toMap(MeetingInfo::getId, Function.identity()));
        List<TaskGroupsResponse.MeetingGroup> groups = perMeeting.entrySet().stream()
                .filter(e -> meetings.containsKey(e.getKey()))
                .map(e -> {
                    MeetingInfo m = meetings.get(e.getKey());
                    return new TaskGroupsResponse.MeetingGroup(m.getId(), m.getName(), m.getExpectedStartTime(), e.getValue());
                })
                .sorted(Comparator.comparing(TaskGroupsResponse.MeetingGroup::startTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new TaskGroupsResponse(dueToday, overdue, mine, assigned, shared, pub, groups);
    }

    // ===================== T3: cả bảng =====================

    @Override
    @Transactional(readOnly = true)
    public BoardResponse board(String userName, String workId, boolean archivedOnly) {
        MeetingTask start = access.find(workId);
        TaskAccess.Rights r = access.requireView(start, userName);
        MeetingTask root = r.root();

        List<MeetingTask> lists = taskRepository.findByParentId(root.getId()).stream().sorted(ORDER).toList();
        List<MeetingTask> allCards = lists.isEmpty() ? List.of()
                : taskRepository.findByParentIdIn(lists.stream().map(MeetingTask::getId).toList());
        List<MeetingTask> cards = allCards.stream().filter(c -> c.isArchived() == archivedOnly).sorted(ORDER).toList();
        int archivedCount = (int) allCards.stream().filter(MeetingTask::isArchived).count();

        CardStats stats = stats(cards.stream().map(MeetingTask::getId).toList());
        Map<String, String> names = names(collectUsers(root, allCards));

        List<BoardListResponse> listDtos = lists.stream()
                .map(l -> new BoardListResponse(l.getId(), l.getTitle(), l.getSortOrder(),
                        cards.stream().filter(c -> l.getId().equals(c.getParentId()))
                                .map(c -> toCard(c, r, userName, stats, names))
                                .toList()))
                .toList();

        List<LabelResponse> labels = labelRepository.findByWorkIdOrderByNameAsc(root.getId()).stream()
                .map(l -> new LabelResponse(l.getId(), l.getName(), l.getColor())).toList();

        return new BoardResponse(toWork(root, r, userName, allCards), listDtos, labels, members(root, allCards, names),
                r.edit(), r.owner(), archivedCount);
    }

    /** Thứ tự cột / thẻ: SortOrder rồi ngày tạo. */
    private static final Comparator<MeetingTask> ORDER = Comparator.comparingInt(MeetingTask::getSortOrder)
            .thenComparing(MeetingTask::getCreateDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MeetingTask::getId);

    private record CardStats(Map<String, List<String>> labels, Map<String, int[]> checklist,
                             Map<String, Integer> comments, Map<String, Integer> files) {
    }

    private CardStats stats(List<String> ids) {
        if (ids.isEmpty()) {
            return new CardStats(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, List<String>> labels = new HashMap<>();
        cardLabelRepository.findByTaskIdIn(ids).forEach(l -> labels.computeIfAbsent(l.getTaskId(), k -> new ArrayList<>()).add(l.getLabelId()));
        Map<String, int[]> checklist = new HashMap<>();
        checklistRepository.findByTaskIdIn(ids).forEach(i -> {
            int[] c = checklist.computeIfAbsent(i.getTaskId(), k -> new int[2]);
            c[1]++;
            if (i.isDone()) c[0]++;
        });
        Map<String, Integer> comments = new HashMap<>();
        commentRepository.findByTaskIdIn(ids).forEach(c -> comments.merge(c.getTaskId(), 1, Integer::sum));
        Map<String, Integer> files = new HashMap<>();
        fileRepository.findByTypeAndReferenceFileIdIn(FILE_TYPE_TASK, ids).forEach(f -> files.merge(f.getReferenceFileId(), 1, Integer::sum));
        return new CardStats(labels, checklist, comments, files);
    }

    private BoardCardResponse toCard(MeetingTask c, TaskAccess.Rights r, String userName, CardStats s, Map<String, String> names) {
        int[] cl = s.checklist().getOrDefault(c.getId(), new int[2]);
        return new BoardCardResponse(c.getId(), c.getParentId(), c.getTitle(), c.getDescription(),
                c.getAssigneeUserName(), c.getAssigneeUserName() == null ? null : names.getOrDefault(c.getAssigneeUserName(), c.getAssigneeUserName()),
                c.getStartDate(), c.getDueDate(), c.getStatus(), c.getPriority(), c.getSortOrder(), c.isArchived(),
                s.labels().getOrDefault(c.getId(), List.of()), cl[0], cl[1],
                s.comments().getOrDefault(c.getId(), 0), s.files().getOrDefault(c.getId(), 0),
                c.getCreateBy(), TaskAccess.canWork(r, c, userName));
    }

    private TaskListItemResponse toWork(MeetingTask w, TaskAccess.Rights r, String userName, List<MeetingTask> cards) {
        String meetingName = null;
        LocalDateTime meetingStart = null;
        if (w.getMeetingId() != null) {
            MeetingInfo m = meetingRepository.findById(w.getMeetingId()).orElse(null);
            if (m != null) {
                meetingName = m.getName();
                meetingStart = m.getExpectedStartTime();
            }
        }
        List<MeetingTask> active = cards.stream().filter(c -> !c.isArchived()).toList();
        int done = (int) active.stream().filter(c -> c.getStatus() == 2).count();
        int progress = active.isEmpty() ? 0 : Math.round(done * 100f / active.size());
        return new TaskListItemResponse(w.getId(), w.getMeetingId(), meetingName, meetingStart, w.getParentId(), w.getLevel(),
                taskRepository.countByParentId(w.getId()), 0, progress, w.getTitle(), w.getDescription(),
                w.getAssigneeUserName(), null, w.getDueDate(), w.getStatus(), w.getPriority(), w.isPublicTask(),
                r.owner(), r.edit(), w.getCreateBy(), w.getCreateDate(), w.getUpdateDate(), w.getSourceRef(),
                w.getStartDate(), w.getSortOrder(), w.getColor(), w.isArchived(), active.size(), done);
    }

    private Set<String> collectUsers(MeetingTask root, List<MeetingTask> cards) {
        Set<String> users = new LinkedHashSet<>();
        users.add(root.getCreateBy());
        cards.forEach(c -> {
            if (c.getAssigneeUserName() != null) users.add(c.getAssigneeUserName());
        });
        shareRepository.findByTaskId(root.getId()).forEach(s -> users.add(s.getUserName()));
        return users;
    }

    private Map<String, String> names(Collection<String> userNames) {
        Map<String, String> map = new HashMap<>();
        accountRepository.findAllById(userNames.stream().filter(u -> u != null).toList())
                .forEach(a -> map.put(a.getUserName(), a.getFullName()));
        return map;
    }

    private List<BoardMemberResponse> members(MeetingTask root, List<MeetingTask> cards, Map<String, String> names) {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put(root.getCreateBy(), "owner");
        for (MeetingTaskShare s : shareRepository.findByTaskId(root.getId())) {
            roles.putIfAbsent(s.getUserName(), s.getPermission() == 1 ? "edit" : "view");
        }
        cards.forEach(c -> {
            if (c.getAssigneeUserName() != null) roles.putIfAbsent(c.getAssigneeUserName(), "assignee");
        });
        return roles.entrySet().stream()
                .map(e -> new BoardMemberResponse(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .toList();
    }

    // ===================== T4: kéo thả =====================

    @Override
    @Transactional
    public void move(String userName, String taskId, MoveTaskRequest request) {
        MeetingTask task = access.find(taskId);
        TaskAccess.Rights r = access.requireEdit(task, userName);
        if (task.getLevel() == 0) {
            throw new IllegalArgumentException("Không thể kéo thả công việc cấp cao nhất.");
        }
        String parentId = request == null || request.parentId() == null || request.parentId().isBlank()
                ? task.getParentId() : request.parentId().trim();
        MeetingTask parent = access.find(parentId);
        if (parent.getLevel() != task.getLevel() - 1 || !access.root(parent).getId().equals(r.root().getId())) {
            throw new IllegalArgumentException("Vị trí thả không hợp lệ.");
        }

        List<MeetingTask> siblings = new ArrayList<>(taskRepository.findByParentId(parentId).stream()
                .filter(t -> !t.getId().equals(taskId))
                .sorted(ORDER)
                .toList());
        int index = siblings.size();
        if (request != null && request.beforeId() != null) {
            for (int i = 0; i < siblings.size(); i++) {
                if (siblings.get(i).getId().equals(request.beforeId())) {
                    index = i;
                    break;
                }
            }
        }
        siblings.add(index, task);
        LocalDateTime now = LocalDateTime.now(clock);
        List<MeetingTask> changed = new ArrayList<>();
        for (int i = 0; i < siblings.size(); i++) {
            MeetingTask t = siblings.get(i);
            boolean moved = t == task && !parentId.equals(t.getParentId());
            if (t.getSortOrder() != i || moved) {
                t.setSortOrder(i);
                if (t == task) {
                    t.setParentId(parentId);
                    t.setUpdateBy(userName);
                    t.setUpdateDate(now);
                }
                changed.add(t);
            }
        }
        if (!changed.isEmpty()) {
            taskRepository.saveAll(changed);
            access.notifyChanged(r.root(), taskId, "moved", userName);
        }
    }

    // ===================== Modal chi tiết =====================

    @Override
    @Transactional(readOnly = true)
    public CardDetailResponse card(String userName, String taskId) {
        MeetingTask card = access.find(taskId);
        TaskAccess.Rights r = access.requireView(card, userName);
        MeetingTask root = r.root();

        CardStats stats = stats(List.of(card.getId()));
        Set<String> users = new LinkedHashSet<>();
        users.add(card.getCreateBy());
        if (card.getAssigneeUserName() != null) users.add(card.getAssigneeUserName());
        List<MtTaskComment> comments = commentRepository.findByTaskIdOrderByCreateDateAsc(taskId);
        comments.forEach(c -> users.add(c.getUserName()));
        List<CmFile> files = fileRepository.findByTypeAndReferenceFileIdOrderByCreateDateDesc(FILE_TYPE_TASK, taskId);
        files.forEach(f -> users.add(f.getCreateBy()));
        Map<String, String> names = names(users);

        String listTitle = card.getParentId() == null ? null
                : taskRepository.findById(card.getParentId()).map(MeetingTask::getTitle).orElse(null);
        return new CardDetailResponse(
                toCard(card, r, userName, stats, names),
                root.getId(),
                listTitle,
                names.getOrDefault(card.getCreateBy(), card.getCreateBy()),
                card.getCreateDate(),
                card.getSourceRef(),
                labelRepository.findByWorkIdOrderByNameAsc(root.getId()).stream()
                        .map(l -> new LabelResponse(l.getId(), l.getName(), l.getColor())).toList(),
                checklistRepository.findByTaskIdOrderBySortOrderAsc(taskId).stream().map(this::toItem).toList(),
                comments.stream().map(c -> toComment(c, names, userName, r)).toList(),
                files.stream().map(f -> toFile(f, userName, r)).toList(),
                r.edit(),
                r.owner());
    }

    // ===================== T6: nhãn =====================

    @Override
    @Transactional
    public LabelResponse createLabel(String userName, String workId, LabelRequest request) {
        TaskAccess.Rights r = access.requireEdit(access.find(workId), userName);
        MtTaskLabel label = new MtTaskLabel();
        label.setId(newId());
        label.setWorkId(r.root().getId());
        applyLabel(label, request);
        labelRepository.save(label);
        access.notifyChanged(r.root(), null, "labels", userName);
        return new LabelResponse(label.getId(), label.getName(), label.getColor());
    }

    @Override
    @Transactional
    public LabelResponse updateLabel(String userName, String labelId, LabelRequest request) {
        MtTaskLabel label = labelRepository.findById(labelId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhãn."));
        TaskAccess.Rights r = access.requireEdit(access.find(label.getWorkId()), userName);
        applyLabel(label, request);
        labelRepository.save(label);
        access.notifyChanged(r.root(), null, "labels", userName);
        return new LabelResponse(label.getId(), label.getName(), label.getColor());
    }

    @Override
    @Transactional
    public void deleteLabel(String userName, String labelId) {
        MtTaskLabel label = labelRepository.findById(labelId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhãn."));
        TaskAccess.Rights r = access.requireEdit(access.find(label.getWorkId()), userName);
        cardLabelRepository.deleteByLabelId(labelId);
        labelRepository.delete(label);
        access.notifyChanged(r.root(), null, "labels", userName);
    }

    private static void applyLabel(MtTaskLabel label, LabelRequest request) {
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.length() > 50) {
            throw new IllegalArgumentException("Tên nhãn tối đa 50 ký tự.");
        }
        String color = request == null || request.color() == null ? "" : request.color().trim();
        if (!COLOR.matcher(color).matches()) {
            throw new IllegalArgumentException("Màu nhãn không hợp lệ (dạng #RRGGBB).");
        }
        label.setName(name);
        label.setColor(color.toUpperCase(Locale.ROOT));
    }

    @Override
    @Transactional
    public List<String> setCardLabels(String userName, String taskId, CardLabelsRequest request) {
        MeetingTask card = access.find(taskId);
        TaskAccess.Rights r = access.requireEdit(card, userName);
        Set<String> allowed = labelRepository.findByWorkIdOrderByNameAsc(r.root().getId()).stream()
                .map(MtTaskLabel::getId).collect(Collectors.toSet());
        List<String> wanted = request == null || request.labelIds() == null ? List.of()
                : request.labelIds().stream().distinct().filter(allowed::contains).toList();
        cardLabelRepository.deleteByTaskId(taskId);
        cardLabelRepository.saveAll(wanted.stream().map(id -> new MtTaskCardLabel(taskId, id)).toList());
        access.notifyChanged(r.root(), taskId, "labels", userName);
        return wanted;
    }

    // ===================== T7: checklist =====================

    @Override
    @Transactional
    public ChecklistItemResponse addChecklistItem(String userName, String taskId, ChecklistItemRequest request) {
        MeetingTask card = access.find(taskId);
        TaskAccess.Rights r = access.requireWork(card, userName);
        String content = text(request == null ? null : request.content(), 500, "Nội dung checklist");
        List<MtTaskChecklistItem> items = checklistRepository.findByTaskIdOrderBySortOrderAsc(taskId);
        MtTaskChecklistItem item = new MtTaskChecklistItem();
        item.setId(newId());
        item.setTaskId(taskId);
        item.setContent(content);
        item.setSortOrder(items.isEmpty() ? 0 : items.get(items.size() - 1).getSortOrder() + 1);
        checklistRepository.save(item);
        access.notifyChanged(r.root(), taskId, "checklist", userName);
        return toItem(item);
    }

    @Override
    @Transactional
    public ChecklistItemResponse updateChecklistItem(String userName, String itemId, ChecklistItemRequest request) {
        MtTaskChecklistItem item = checklistRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mục checklist."));
        TaskAccess.Rights r = access.requireWork(access.find(item.getTaskId()), userName);
        if (request != null && request.content() != null) {
            item.setContent(text(request.content(), 500, "Nội dung checklist"));
        }
        if (request != null && request.done() != null) {
            item.setDone(request.done());
        }
        checklistRepository.save(item);
        access.notifyChanged(r.root(), item.getTaskId(), "checklist", userName);
        return toItem(item);
    }

    @Override
    @Transactional
    public void deleteChecklistItem(String userName, String itemId) {
        MtTaskChecklistItem item = checklistRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mục checklist."));
        TaskAccess.Rights r = access.requireWork(access.find(item.getTaskId()), userName);
        checklistRepository.delete(item);
        access.notifyChanged(r.root(), item.getTaskId(), "checklist", userName);
    }

    private ChecklistItemResponse toItem(MtTaskChecklistItem i) {
        return new ChecklistItemResponse(i.getId(), i.getContent(), i.isDone(), i.getSortOrder());
    }

    // ===================== T8: bình luận =====================

    @Override
    @Transactional
    public CommentResponse addComment(String userName, String taskId, CommentRequest request) {
        MeetingTask card = access.find(taskId);
        TaskAccess.Rights r = access.requireWork(card, userName);
        MtTaskComment c = new MtTaskComment();
        c.setId(newId());
        c.setTaskId(taskId);
        c.setUserName(userName);
        c.setContent(text(request == null ? null : request.content(), 2000, "Bình luận"));
        c.setCreateDate(LocalDateTime.now(clock));
        commentRepository.save(c);
        access.notifyChanged(r.root(), taskId, "comment", userName);
        return toComment(c, names(List.of(userName)), userName, r);
    }

    @Override
    @Transactional
    public void deleteComment(String userName, String commentId) {
        MtTaskComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận."));
        TaskAccess.Rights r = access.requireView(access.find(c.getTaskId()), userName);
        if (!TaskAccess.eq(c.getUserName(), userName) && !r.owner()) {
            throw new SecurityException("Chỉ người viết hoặc người tạo công việc được xoá bình luận.");
        }
        commentRepository.delete(c);
        access.notifyChanged(r.root(), c.getTaskId(), "comment", userName);
    }

    private CommentResponse toComment(MtTaskComment c, Map<String, String> names, String userName, TaskAccess.Rights r) {
        return new CommentResponse(c.getId(), c.getUserName(), names.getOrDefault(c.getUserName(), c.getUserName()),
                c.getContent(), c.getCreateDate(), TaskAccess.eq(c.getUserName(), userName) || r.owner());
    }

    // ===================== T9: đính kèm =====================

    @Override
    @Transactional
    public TaskFileResponse uploadFile(String userName, String taskId, MultipartFile file) {
        MeetingTask card = access.find(taskId);
        TaskAccess.Rights r = access.requireWork(card, userName);
        if (storage == null) {
            throw new IllegalStateException("Kho lưu trữ file chưa sẵn sàng.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn file.");
        }
        int maxMb = DEFAULT_MAX_MB;
        Set<String> allowed = DEFAULT_EXTENSIONS;
        if (settings != null) {
            try {
                var s = settings.getPublicSettings();
                if (s.maxUploadSizeMb() > 0) maxMb = s.maxUploadSizeMb();
                if (s.allowedExtensions() != null && !s.allowedExtensions().isEmpty()) {
                    allowed = s.allowedExtensions().stream()
                            .map(e -> (e.startsWith(".") ? e : "." + e).toLowerCase(Locale.ROOT))
                            .collect(Collectors.toSet());
                }
            } catch (RuntimeException ignored) {
                // dùng giới hạn mặc định
            }
        }
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new IllegalArgumentException("File vượt quá " + maxMb + "MB.");
        }
        String original = cleanName(file.getOriginalFilename());
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (ext.isEmpty() || !allowed.contains(ext)) {
            throw new IllegalArgumentException("Định dạng " + (ext.isEmpty() ? "(không có)" : ext) + " không được hỗ trợ.");
        }

        String id = newId();
        String objectName = "tasks/" + r.root().getId() + "/" + taskId + "/" + id + ext;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try (InputStream is = file.getInputStream()) {
            storage.upload(objectName, is, file.getSize(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Lỗi đọc file tải lên.", e);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        CmFile f = new CmFile();
        f.setId(id);
        f.setFileName(original);
        f.setFileSize(BigDecimal.valueOf(file.getSize()));
        f.setMimeType(contentType);
        f.setExtension(ext);
        f.setType(FILE_TYPE_TASK);
        f.setIcon("doc");
        f.setReferenceFileId(taskId);
        f.setMinutesFile(false);
        f.setOrderNumber(0);
        f.setVoiceToText("");
        f.setBucketName(storage.getBucketName());
        f.setObjectName(objectName);
        f.setCreateBy(userName);
        f.setCreateDate(now);
        f.setUpdateBy(userName);
        f.setUpdateDate(now);
        try {
            fileRepository.save(f);
        } catch (RuntimeException e) {
            storage.delete(objectName);
            throw e;
        }
        access.notifyChanged(r.root(), taskId, "file", userName);
        return toFile(f, userName, r);
    }

    @Override
    @Transactional
    public void deleteFile(String userName, String fileId) {
        CmFile f = taskFile(fileId);
        TaskAccess.Rights r = access.requireWork(access.find(f.getReferenceFileId()), userName);
        if (!TaskAccess.eq(f.getCreateBy(), userName) && !r.edit()) {
            throw new SecurityException("Chỉ người tải lên hoặc người có quyền sửa được xoá file.");
        }
        fileRepository.delete(f);
        if (storage != null) {
            try { storage.delete(f.getObjectName()); } catch (RuntimeException ignored) { /* object đã mất */ }
        }
        access.notifyChanged(r.root(), f.getReferenceFileId(), "file", userName);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResponse downloadUrl(String userName, String fileId) {
        CmFile f = taskFile(fileId);
        access.requireView(access.find(f.getReferenceFileId()), userName);
        if (storage == null) {
            throw new IllegalStateException("Kho lưu trữ file chưa sẵn sàng.");
        }
        return new FileDownloadResponse(storage.getDownloadUrl(f.getObjectName(), 900), 900);
    }

    private CmFile taskFile(String fileId) {
        return fileRepository.findById(fileId)
                .filter(f -> f.getType() == FILE_TYPE_TASK)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy file."));
    }

    private TaskFileResponse toFile(CmFile f, String userName, TaskAccess.Rights r) {
        return new TaskFileResponse(f.getId(), f.getFileName(), f.getFileSize(), f.getMimeType(), f.getCreateBy(),
                f.getCreateDate(), TaskAccess.eq(f.getCreateBy(), userName) || r.edit());
    }

    // ===================== Xoá dữ liệu phụ =====================

    @Override
    @Transactional
    public void purge(Collection<String> taskIds, String workIdIfBoardDeleted) {
        for (String id : taskIds) {
            cardLabelRepository.deleteByTaskId(id);
            checklistRepository.deleteByTaskId(id);
            commentRepository.deleteByTaskId(id);
        }
        if (!taskIds.isEmpty()) {
            List<CmFile> files = fileRepository.findByTypeAndReferenceFileIdIn(FILE_TYPE_TASK, taskIds);
            fileRepository.deleteAll(files);
            if (storage != null) {
                files.forEach(f -> {
                    try { storage.delete(f.getObjectName()); } catch (RuntimeException ignored) { /* bỏ qua */ }
                });
            }
        }
        if (workIdIfBoardDeleted != null) {
            labelRepository.deleteByWorkId(workIdIfBoardDeleted);
        }
    }

    // ===================== Tiện ích =====================

    private static String text(String value, int max, String field) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(field + " không được để trống.");
        }
        if (v.length() > max) {
            throw new IllegalArgumentException(field + " tối đa " + max + " ký tự.");
        }
        return v;
    }

    private static String cleanName(String name) {
        if (name == null) return "file";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String n = slash >= 0 ? name.substring(slash + 1) : name;
        return n.isBlank() ? "file" : n;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
