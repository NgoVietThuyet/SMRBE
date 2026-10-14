package vn.com.d2s.smr.dto.mt.task.board;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChecklistItemResponse(String id, String content, @JsonProperty("isDone") boolean done, int sortOrder) {
}
