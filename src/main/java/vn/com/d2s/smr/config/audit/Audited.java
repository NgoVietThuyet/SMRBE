package vn.com.d2s.smr.config.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method controller cần ghi SyAuditLogs. AuditInterceptor ghi sau khi request xong (thành công hoặc lỗi),
 * kèm body request (đã lọc khóa nhạy cảm) và path variable.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Nhóm: AUTH, ACCOUNT, PERMISSION, ORGANIZATION, TITLE, SETTINGS (phase sau thêm MEETING, MINUTES, FILE). */
    String category();

    /** Mã hành động, ví dụ LOGIN, CREATE, UPDATE, DELETE, MOVE, LOCK... */
    String action();

    /** Loại đối tượng bị tác động (rỗng nếu không có). */
    String targetType() default "";

    /** Tên path variable chứa id đối tượng (ví dụ "id", "userName", "code"). */
    String targetPathVariable() default "";

    /** Khi không có path variable: lấy id đối tượng từ trường này của body (ví dụ "userName", "code"). */
    String targetBodyField() default "";

    /** Chụp quyền hiện tại trước khi sửa để ghi "before" (dùng cho PUT /{type}/{id}/permissions). */
    boolean snapshotPermission() default false;
}
