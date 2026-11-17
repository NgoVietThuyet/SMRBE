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
        List<ActionItem> actions,
        List<Discussion> discussion
) {
    public MinutesContent {
        summary = summary == null ? "" : summary;
        summarySources = summarySources == null ? new ArrayList<>() : summarySources;
        decisions = decisions == null ? new ArrayList<>() : decisions;
        actions = actions == null ? new ArrayList<>() : actions;
        discussion = discussion == null ? new ArrayList<>() : discussion;
    }

    /** Biên bản không có phần diễn biến thảo luận (bản nhập tay, bản cũ, bản sinh bằng luồng cũ). */
    public MinutesContent(String summary, List<String> summarySources, List<Decision> decisions, List<ActionItem> actions) {
        this(summary, summarySources, decisions, actions, null);
    }

    /** Diễn biến thảo luận theo từng chủ đề: ai đề xuất gì, ai phản biện, cuối cùng chốt ra sao (để lọc ý kiến đã bị bác bỏ). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Discussion(String topic, String text, List<String> sources) {
        public Discussion {
            sources = sources == null ? new ArrayList<>() : sources;
        }
    }

    public static MinutesContent empty() {
        return new MinutesContent("", List.of(), List.of(), List.of(), List.of());
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
