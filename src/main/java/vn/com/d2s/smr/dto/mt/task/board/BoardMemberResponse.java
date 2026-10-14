package vn.com.d2s.smr.dto.mt.task.board;

/** role: owner · edit · view · assignee. */
public record BoardMemberResponse(String userName, String fullName, String role) {
}
