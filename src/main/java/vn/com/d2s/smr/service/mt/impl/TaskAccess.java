package vn.com.d2s.smr.service.mt.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskShareRepository;
import vn.com.d2s.smr.service.nt.UserNotificationPublisher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quyền trên bảng công việc (P12): xét ở <b>công việc cấp 0</b> rồi áp cho cả cây (đầu việc, việc nhỏ).
 * <ul>
 *   <li><b>owner</b> — người tạo công việc cấp 0: mọi thao tác, chia sẻ, công khai/riêng tư, xoá bảng.</li>
 *   <li><b>edit</b> — owner, người được chia sẻ quyền Sửa trên bảng (hoặc trên chính việc), người tạo chính việc đó.</li>
 *   <li><b>view</b> — edit, bảng công khai, được chia sẻ Xem, thành viên cuộc họp của bảng, người thực hiện một việc trong bảng.</li>
 *   <li><b>work</b> trên một việc — edit hoặc là người thực hiện việc đó (tick hoàn thành, checklist, bình luận, đính kèm).</li>
 * </ul>
 */
@Component
public class TaskAccess {

    public record Rights(MeetingTask root, boolean view, boolean edit, boolean owner) {
    }

    private final MeetingTaskRepository taskRepository;
    private final MeetingTaskShareRepository shareRepository;
    private final MeetingPersonalRepository personalRepository;
    private UserNotificationPublisher publisher;

    public TaskAccess(
            MeetingTaskRepository taskRepository,
            MeetingTaskShareRepository shareRepository,
            MeetingPersonalRepository personalRepository
    ) {
        this.taskRepository = taskRepository;
        this.shareRepository = shareRepository;
        this.personalRepository = personalRepository;
    }

    @Autowired(required = false)
    void setPublisher(UserNotificationPublisher publisher) {
        this.publisher = publisher;
    }

    public MeetingTask root(MeetingTask task) {
        MeetingTask cur = task;
        for (int i = 0; i < 3 && cur.getParentId() != null; i++) {
            Optional<MeetingTask> parent = taskRepository.findById(cur.getParentId());
            if (parent.isEmpty()) {
                break;
            }
            cur = parent.get();
        }
        return cur;
    }

    public MeetingTask find(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc."));
    }

    public Rights rights(MeetingTask task, String user) {
        MeetingTask root = root(task);
        boolean owner = eq(root.getCreateBy(), user);
        Optional<MeetingTaskShare> rootShare = shareRepository.findByTaskIdAndUserName(root.getId(), user);
        Optional<MeetingTaskShare> ownShare = task == root ? Optional.empty() : shareRepository.findByTaskIdAndUserName(task.getId(), user);
        boolean edit = owner
                || eq(task.getCreateBy(), user)
                || rootShare.map(s -> s.getPermission() == 1).orElse(false)
                || ownShare.map(s -> s.getPermission() == 1).orElse(false);
        boolean view = edit
                || root.isPublicTask()
                || rootShare.isPresent()
                || ownShare.isPresent()
                || eq(task.getAssigneeUserName(), user)
                || (root.getMeetingId() != null && personalRepository.existsByMeetingIdAndUserName(root.getMeetingId(), user))
                || tree(root).stream().anyMatch(t -> eq(t.getAssigneeUserName(), user));
        return new Rights(root, view, edit, owner);
    }

    public Rights requireView(MeetingTask task, String user) {
        Rights r = rights(task, user);
        if (!r.view()) {
            throw new SecurityException("Bạn không có quyền xem công việc này.");
        }
        return r;
    }

    public Rights requireEdit(MeetingTask task, String user) {
        Rights r = rights(task, user);
        if (!r.edit()) {
            throw new SecurityException(r.view() ? "Bạn chỉ có quyền xem công việc này." : "Bạn không có quyền với công việc này.");
        }
        return r;
    }

    public static boolean canWork(Rights r, MeetingTask task, String user) {
        return r.edit() || eq(task.getAssigneeUserName(), user);
    }

    public Rights requireWork(MeetingTask task, String user) {
        Rights r = rights(task, user);
        if (!canWork(r, task, user)) {
            throw new SecurityException("Chỉ người thực hiện hoặc người có quyền sửa mới thao tác được trên việc này.");
        }
        return r;
    }

    /** Đầu việc + việc nhỏ của một công việc cấp 0 (2 truy vấn). */
    public List<MeetingTask> tree(MeetingTask root) {
        List<MeetingTask> lists = taskRepository.findByParentId(root.getId());
        List<MeetingTask> all = new ArrayList<>(lists);
        if (!lists.isEmpty()) {
            all.addAll(taskRepository.findByParentIdIn(lists.stream().map(MeetingTask::getId).toList()));
        }
        return all;
    }

    /** Người tạo + người được chia sẻ + người thực hiện các việc trong bảng (để gửi TaskChanged). */
    public Set<String> members(MeetingTask root) {
        Set<String> users = new LinkedHashSet<>();
        users.add(root.getCreateBy());
        shareRepository.findByTaskId(root.getId()).forEach(s -> users.add(s.getUserName()));
        if (root.getAssigneeUserName() != null) users.add(root.getAssigneeUserName());
        tree(root).forEach(t -> {
            if (t.getAssigneeUserName() != null) users.add(t.getAssigneeUserName());
        });
        return users;
    }

    /** T10: báo thành viên bảng có thay đổi (kênh theo người dùng — trang bảng tự tải lại). */
    public void notifyChanged(MeetingTask root, String taskId, String action, String actor) {
        if (publisher == null || root == null) {
            return;
        }
        try {
            publisher.publishToUsers(members(root), "TaskChanged", Map.of(
                    "workId", root.getId(),
                    "taskId", taskId == null ? root.getId() : taskId,
                    "action", action,
                    "by", actor == null ? "" : actor));
        } catch (RuntimeException ignored) {
            // realtime lỗi không làm hỏng thao tác chính
        }
    }

    static boolean eq(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }
}
