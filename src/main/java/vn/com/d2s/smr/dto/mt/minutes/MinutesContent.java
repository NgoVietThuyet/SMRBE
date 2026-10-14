package vn.com.d2s.smr.dto.mt.minutes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Nội dung biên bản (lưu ở MtMinutes.ContentJson). sources = Id đoạn transcript (MeetingTranscriptSegments)
 * làm nguồn — bấm chip timestamp ở FE để cuộn tới đoạn đó.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MinutesContent(
        String summary,
        List<String> summarySources,
        List<Decision> decisions,
        List<ActionItem> actions
) {
    public MinutesContent {
        summary = summary == null ? "" : summary;
        summarySources = summarySources == null ? new ArrayList<>() : summarySources;
        decisions = decisions == null ? new ArrayList<>() : decisions;
        actions = actions == null ? new ArrayList<>() : actions;
    }

    public static MinutesContent empty() {
        return new MinutesContent("", List.of(), List.of(), List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Decision(String id, String text, List<String> sources) {
        public Decision {
            sources = sources == null ? new ArrayList<>() : sources;
        }
    }

    /** assignee / dueDate (yyyy-MM-dd) null = "Chưa xác định". confirmed = được tạo thành công việc khi phát hành. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActionItem(String id, String text, String assignee, String assigneeName, String dueDate,
                             List<String> sources, boolean confirmed, String taskId) {
        public ActionItem {
            sources = sources == null ? new ArrayList<>() : sources;
        }
    }
}
