package vn.com.d2s.smr.dto.mt.task.board;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.com.d2s.smr.dto.mt.task.TaskListItemResponse;

import java.util.List;

/** Cả bảng trong 1 request (GET Task/{workId}/board). */
public record BoardResponse(
        TaskListItemResponse work,
        List<BoardListResponse> lists,
        List<LabelResponse> labels,
        List<BoardMemberResponse> members,
        boolean canEdit,
        @JsonProperty("isOwner") boolean owner,
        int archivedCount
) {
}
