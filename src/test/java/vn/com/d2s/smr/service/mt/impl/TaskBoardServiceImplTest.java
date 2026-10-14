package vn.com.d2s.smr.service.mt.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.com.d2s.smr.dto.mt.task.board.BoardResponse;
import vn.com.d2s.smr.dto.mt.task.board.CardLabelsRequest;
import vn.com.d2s.smr.dto.mt.task.board.ChecklistItemRequest;
import vn.com.d2s.smr.dto.mt.task.board.CommentRequest;
import vn.com.d2s.smr.dto.mt.task.board.LabelRequest;
import vn.com.d2s.smr.dto.mt.task.board.MoveTaskRequest;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.entity.mt.MtTaskCardLabel;
import vn.com.d2s.smr.entity.mt.MtTaskChecklistItem;
import vn.com.d2s.smr.entity.mt.MtTaskLabel;
import vn.com.d2s.smr.repository.ad.AdAccountRepository;
import vn.com.d2s.smr.repository.cf.CmFileRepository;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.repository.mt.MtTaskCardLabelRepository;
import vn.com.d2s.smr.repository.mt.MtTaskChecklistItemRepository;
import vn.com.d2s.smr.repository.mt.MtTaskCommentRepository;
import vn.com.d2s.smr.repository.mt.MtTaskLabelRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskBoardServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
    private static final LocalDateTime T0 = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock private MeetingTaskRepository taskRepository;
    @Mock private MeetingTaskShareRepository shareRepository;
    @Mock private MeetingInfoRepository meetingRepository;
    @Mock private MeetingPersonalRepository personalRepository;
    @Mock private AdAccountRepository accountRepository;
    @Mock private MtTaskLabelRepository labelRepository;
    @Mock private MtTaskCardLabelRepository cardLabelRepository;
    @Mock private MtTaskChecklistItemRepository checklistRepository;
    @Mock private MtTaskCommentRepository commentRepository;
    @Mock private CmFileRepository fileRepository;

    private final Map<String, MeetingTask> db = new HashMap<>();
    private TaskBoardServiceImpl service;

    /** Bảng mẫu: owner tạo; cột L1 (c1, c2, c3), cột L2 (c4 giao cho "worker"). */
    @BeforeEach
    void setUp() {
        TaskAccess access = new TaskAccess(taskRepository, shareRepository, personalRepository);
        service = new TaskBoardServiceImpl(taskRepository, shareRepository, meetingRepository, accountRepository,
                labelRepository, cardLabelRepository, checklistRepository, commentRepository, fileRepository, access,
                Clock.fixed(NOW, ZoneOffset.UTC));

        put(task("W", null, 0, 0, "owner", null));
        put(task("L1", "W", 1, 0, "owner", null));
        put(task("L2", "W", 1, 1, "owner", null));
        put(task("c1", "L1", 2, 0, "owner", null));
        put(task("c2", "L1", 2, 1, "owner", null));
        put(task("c3", "L1", 2, 2, "owner", null));
        MeetingTask c4 = task("c4", "L2", 2, 0, "owner", "worker");
        c4.setStatus(2);
        put(c4);

        when(taskRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(db.get((String) i.getArgument(0))));
        when(taskRepository.findByParentId(anyString())).thenAnswer(i -> children((String) i.getArgument(0)));
        when(taskRepository.findByParentIdIn(anyCollection())).thenAnswer(i -> {
            List<MeetingTask> out = new ArrayList<>();
            ((Collection<String>) i.getArgument(0)).forEach(p -> out.addAll(children(p)));
            return out;
        });
        when(shareRepository.findByTaskIdAndUserName(anyString(), anyString())).thenReturn(Optional.empty());
        when(shareRepository.findByTaskId(anyString())).thenReturn(List.of());
    }

    // ===== Quyền

    @Test
    void viewerShareCannotMoveOrEditLabels() {
        share("W", "viewer", 0);

        assertThatThrownBy(() -> service.move("viewer", "c1", new MoveTaskRequest("L2", null)))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.createLabel("viewer", "W", new LabelRequest("Gấp", "#EB5A46")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.setCardLabels("viewer", "c1", new CardLabelsRequest(List.of())))
                .isInstanceOf(SecurityException.class);
        // nhưng vẫn xem được bảng
        assertThat(service.board("viewer", "W").canEdit()).isFalse();
    }

    @Test
    void strangerCannotViewPrivateBoard() {
        assertThatThrownBy(() -> service.board("stranger", "W")).isInstanceOf(SecurityException.class);
    }

    @Test
    void assigneeCanWorkOnOwnCardButNotOthers() {
        when(checklistRepository.findByTaskIdOrderBySortOrderAsc("c4")).thenReturn(List.of());

        assertThat(service.addChecklistItem("worker", "c4", new ChecklistItemRequest("Viết báo cáo", null)).content())
                .isEqualTo("Viết báo cáo");
        assertThatThrownBy(() -> service.addComment("worker", "c1", new CommentRequest("hi")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.move("worker", "c4", new MoveTaskRequest("L1", null)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void editShareOnBoardCanMove() {
        share("W", "editor", 1);
        service.move("editor", "c3", new MoveTaskRequest("L1", "c1"));
        assertThat(db.get("c3").getSortOrder()).isZero();
    }

    // ===== Kéo thả

    @Test
    void moveWithinColumnReordersSiblings() {
        service.move("owner", "c3", new MoveTaskRequest("L1", "c1"));

        assertThat(db.get("c3").getSortOrder()).isEqualTo(0);
        assertThat(db.get("c1").getSortOrder()).isEqualTo(1);
        assertThat(db.get("c2").getSortOrder()).isEqualTo(2);
    }

    @Test
    void moveToOtherColumnChangesParentAndAppends() {
        service.move("owner", "c1", new MoveTaskRequest("L2", null));

        assertThat(db.get("c1").getParentId()).isEqualTo("L2");
        assertThat(db.get("c1").getSortOrder()).isEqualTo(1);
        assertThat(db.get("c2").getSortOrder()).isEqualTo(1); // giữ nguyên thứ tự cũ trong L1 (chỉ cột đích được đánh lại)
    }

    @Test
    void moveRejectsWrongLevelOrOtherBoard() {
        put(task("X", null, 0, 0, "owner", null));
        put(task("XL", "X", 1, 0, "owner", null));

        assertThatThrownBy(() -> service.move("owner", "c1", new MoveTaskRequest("W", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.move("owner", "c1", new MoveTaskRequest("XL", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveListReordersColumns() {
        service.move("owner", "L2", new MoveTaskRequest("W", "L1"));

        assertThat(db.get("L2").getSortOrder()).isZero();
        assertThat(db.get("L1").getSortOrder()).isEqualTo(1);
    }

    // ===== Cả bảng

    @Test
    void boardAggregatesListsCardsAndBadges() {
        MtTaskLabel label = new MtTaskLabel();
        label.setId("lb1");
        label.setWorkId("W");
        label.setName("Gấp");
        label.setColor("#EB5A46");
        when(labelRepository.findByWorkIdOrderByNameAsc("W")).thenReturn(List.of(label));
        when(cardLabelRepository.findByTaskIdIn(anyCollection())).thenReturn(List.of(new MtTaskCardLabel("c1", "lb1")));
        MtTaskChecklistItem done = item("c1", true);
        MtTaskChecklistItem open = item("c1", false);
        when(checklistRepository.findByTaskIdIn(anyCollection())).thenReturn(List.of(done, open));
        MeetingTask archived = task("c9", "L1", 2, 9, "owner", null);
        archived.setArchived(true);
        put(archived);

        BoardResponse board = service.board("owner", "W");

        assertThat(board.lists()).extracting(l -> l.id()).containsExactly("L1", "L2");
        assertThat(board.lists().get(0).cards()).extracting(c -> c.id()).containsExactly("c1", "c2", "c3");
        assertThat(board.archivedCount()).isEqualTo(1);
        var c1 = board.lists().get(0).cards().get(0);
        assertThat(c1.labelIds()).containsExactly("lb1");
        assertThat(c1.checklistDone()).isEqualTo(1);
        assertThat(c1.checklistTotal()).isEqualTo(2);
        assertThat(board.work().cardCount()).isEqualTo(4);
        assertThat(board.work().cardDoneCount()).isEqualTo(1);
        assertThat(board.work().progress()).isEqualTo(25);
        assertThat(board.owner()).isTrue();
        assertThat(board.members()).extracting(m -> m.role()).contains("owner", "assignee");
    }

    @Test
    void setCardLabelsIgnoresLabelsOfOtherBoards() {
        MtTaskLabel mine = new MtTaskLabel();
        mine.setId("lb1");
        mine.setWorkId("W");
        when(labelRepository.findByWorkIdOrderByNameAsc("W")).thenReturn(List.of(mine));

        List<String> saved = service.setCardLabels("owner", "c1", new CardLabelsRequest(List.of("lb1", "foreign")));

        assertThat(saved).containsExactly("lb1");
    }

    @Test
    void labelColorMustBeHex() {
        assertThatThrownBy(() -> service.createLabel("owner", "W", new LabelRequest("x", "red")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(labelRepository, never()).save(any());
    }

    // ===== Tiện ích

    private void put(MeetingTask t) {
        db.put(t.getId(), t);
    }

    private List<MeetingTask> children(String parentId) {
        return db.values().stream().filter(t -> parentId.equals(t.getParentId())).toList();
    }

    private void share(String taskId, String user, int permission) {
        MeetingTaskShare s = new MeetingTaskShare();
        s.setTaskId(taskId);
        s.setUserName(user);
        s.setPermission(permission);
        when(shareRepository.findByTaskIdAndUserName(taskId, user)).thenReturn(Optional.of(s));
    }

    private static MeetingTask task(String id, String parent, int level, int order, String createBy, String assignee) {
        MeetingTask t = new MeetingTask();
        t.setId(id);
        t.setParentId(parent);
        t.setLevel(level);
        t.setSortOrder(order);
        t.setTitle(id);
        t.setCreateBy(createBy);
        t.setCreateDate(T0.plusMinutes(order));
        t.setAssigneeUserName(assignee);
        return t;
    }

    private static MtTaskChecklistItem item(String taskId, boolean done) {
        MtTaskChecklistItem i = new MtTaskChecklistItem();
        i.setId(taskId + done);
        i.setTaskId(taskId);
        i.setContent("x");
        i.setDone(done);
        return i;
    }
}
