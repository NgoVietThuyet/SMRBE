package vn.com.d2s.smr.service.mt;

import org.springframework.web.multipart.MultipartFile;
import vn.com.d2s.smr.dto.cf.file.FileDownloadResponse;
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

import java.util.Collection;
import java.util.List;

/**
 * P12 — bảng công việc kiểu Trello: sidebar, cả bảng, kéo thả, nhãn, checklist, bình luận, đính kèm.
 * Lỗi: IllegalArgumentException → 404/400 · SecurityException → 403 · IllegalStateException → 409.
 */
public interface TaskBoardService {

    TaskGroupsResponse groups(String userName);

    default BoardResponse board(String userName, String workId) {
        return board(userName, workId, false);
    }

    /** archivedOnly = true: chỉ trả các thẻ đã lưu trữ (menu "Thẻ đã lưu trữ"). */
    BoardResponse board(String userName, String workId, boolean archivedOnly);

    void move(String userName, String taskId, MoveTaskRequest request);

    CardDetailResponse card(String userName, String taskId);

    LabelResponse createLabel(String userName, String workId, LabelRequest request);

    LabelResponse updateLabel(String userName, String labelId, LabelRequest request);

    void deleteLabel(String userName, String labelId);

    List<String> setCardLabels(String userName, String taskId, CardLabelsRequest request);

    ChecklistItemResponse addChecklistItem(String userName, String taskId, ChecklistItemRequest request);

    ChecklistItemResponse updateChecklistItem(String userName, String itemId, ChecklistItemRequest request);

    void deleteChecklistItem(String userName, String itemId);

    CommentResponse addComment(String userName, String taskId, CommentRequest request);

    void deleteComment(String userName, String commentId);

    TaskFileResponse uploadFile(String userName, String taskId, MultipartFile file);

    void deleteFile(String userName, String fileId);

    FileDownloadResponse downloadUrl(String userName, String fileId);

    /** Xoá dữ liệu phụ (nhãn gắn, checklist, bình luận, file) của các việc bị xoá. */
    void purge(Collection<String> taskIds, String workIdIfBoardDeleted);
}
