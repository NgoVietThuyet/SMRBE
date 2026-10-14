package vn.com.d2s.smr.service.mt.impl;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.mt.MeetingTaskShare;

import java.time.LocalDateTime;

/** Điều kiện truy vấn công việc dùng chung (danh sách, sidebar P12). */
final class TaskSpecs {

    private TaskSpecs() {
    }

    /** Việc người dùng xem được: tự tạo, công khai, được giao, được chia sẻ, thuộc cuộc họp mình tham gia. */
    static Specification<MeetingTask> viewable(String userName) {
        return (root, query, cb) -> {
            Subquery<String> shares = query.subquery(String.class);
            Root<MeetingTaskShare> s = shares.from(MeetingTaskShare.class);
            shares.select(s.get("taskId")).where(cb.equal(s.get("userName"), userName));

            Subquery<String> meetings = query.subquery(String.class);
            Root<MeetingPersonal> p = meetings.from(MeetingPersonal.class);
            meetings.select(p.get("meetingId")).where(cb.equal(p.get("userName"), userName));

            return cb.or(
                    cb.equal(root.get("createBy"), userName),
                    cb.isTrue(root.get("publicTask")),
                    cb.equal(root.get("assigneeUserName"), userName),
                    root.get("id").in(shares),
                    root.get("meetingId").in(meetings));
        };
    }

    static Specification<MeetingTask> notArchived() {
        return (root, query, cb) -> cb.isFalse(root.get("archived"));
    }

    static Specification<MeetingTask> level(int level) {
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    /**
     * Nhóm ở sidebar (áp cho công việc cấp 0):
     * mine = tôi tạo · assigned = tôi thực hiện ít nhất một việc trong bảng (không phải người tạo) ·
     * shared = được chia sẻ · public = công khai.
     */
    static Specification<MeetingTask> group(String group, String userName) {
        String g = group == null ? "" : group.trim().toLowerCase();
        return switch (g) {
            case "mine" -> (root, query, cb) -> cb.equal(root.get("createBy"), userName);
            case "shared" -> (root, query, cb) -> {
                Subquery<String> shares = query.subquery(String.class);
                Root<MeetingTaskShare> s = shares.from(MeetingTaskShare.class);
                shares.select(s.get("taskId")).where(cb.equal(s.get("userName"), userName));
                return root.get("id").in(shares);
            };
            case "public" -> (root, query, cb) -> cb.isTrue(root.get("publicTask"));
            case "assigned" -> (root, query, cb) -> {
                // Đầu việc (cấp 1) giao cho tôi, hoặc chứa việc nhỏ (cấp 2) giao cho tôi.
                Subquery<String> cards = query.subquery(String.class);
                Root<MeetingTask> c = cards.from(MeetingTask.class);
                cards.select(c.get("parentId")).where(cb.equal(c.get("assigneeUserName"), userName));

                Subquery<String> lists = query.subquery(String.class);
                Root<MeetingTask> l = lists.from(MeetingTask.class);
                lists.select(l.get("parentId")).where(cb.or(
                        cb.equal(l.get("assigneeUserName"), userName),
                        l.get("id").in(cards)));

                Predicate notMine = cb.notEqual(root.get("createBy"), userName);
                return cb.and(notMine, cb.or(cb.equal(root.get("assigneeUserName"), userName), root.get("id").in(lists)));
            };
            default -> null;
        };
    }

    /** Việc giao cho tôi, chưa xong, hạn trong [from, to). */
    static Specification<MeetingTask> myDue(String userName, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            Predicate p = cb.and(
                    cb.equal(root.get("assigneeUserName"), userName),
                    cb.notEqual(root.get("status"), 2),
                    cb.isFalse(root.get("archived")),
                    cb.isNotNull(root.get("dueDate")),
                    cb.lessThan(root.get("dueDate"), to));
            return from == null ? p : cb.and(p, cb.greaterThanOrEqualTo(root.get("dueDate"), from));
        };
    }
}
