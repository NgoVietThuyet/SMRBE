package vn.com.d2s.smr.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Nội dung bộ dữ liệu demo (src/main/resources/seed/demo-data.json). Chỉ là dữ liệu, không có logic.
 * Ngày tính tương đối: {@code day} = số ngày so với ngày chạy seed (giờ Việt Nam).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DemoData(
        int version,
        String company,
        String emailDomain,
        List<Account> accounts,
        List<String> managerTitles,
        List<String> junkAccounts,
        List<Recurring> recurring,
        List<Meeting> meetings,
        Map<String, List<Line>> transcripts,
        Map<String, ManualMinutes> manualMinutes,
        List<Board> boards,
        Map<String, String> meetingTemplates
) {
    public static final String RESOURCE = "/seed/demo-data.json";

    public static DemoData load(ObjectMapper mapper) {
        try (InputStream in = DemoData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Không tìm thấy " + RESOURCE);
            return mapper.readValue(in, DemoData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + RESOURCE, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String userName, String fullName, String orgId, String titleCode, String phone,
                          boolean locked, boolean mustChangePassword, boolean denyAiSummary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recurring(String key, String name, int weekday, int weeksBefore, int weeksAfter, String start, int minutes,
                            String host, String secretary, List<String> cohost, List<String> required, List<String> optional,
                            String description, String agenda, int visibility) {
    }

    /**
     * status: ended · ongoing · scheduled · cancelled · draft · archived.
     * start: "HH:mm", hoặc "NOW-15" / "NOW+180" (phút so với lúc chạy).
     * minutesFlow: published · submitted · rejected · draft · manual (null = không tạo biên bản).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meeting(String key, String name, int day, String start, int minutes, String host, String status,
                          List<String> cohost, String secretary, List<String> required, List<String> optional,
                          String description, String agenda, Integer visibility, boolean lobby, boolean allowGuests,
                          String transcript, String minutesFlow, String rejectReason, String cancelReason,
                          List<String> files, Attendance attendance) {
        public List<String> cohost() { return cohost == null ? List.of() : cohost; }
        public List<String> required() { return required == null ? List.of() : required; }
        public List<String> optional() { return optional == null ? List.of() : optional; }
        public List<String> files() { return files == null ? List.of() : files; }
        public Attendance attendance() { return attendance == null ? new Attendance(null, null, null, null) : attendance; }
    }

    /** late / leaveEarly: phút; reconnect: rớt mạng giữa chừng rồi vào lại; absent: vắng mặt. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attendance(Map<String, Integer> late, Map<String, Integer> leaveEarly, List<String> reconnect, List<String> absent) {
        public Map<String, Integer> late() { return late == null ? Map.of() : late; }
        public Map<String, Integer> leaveEarly() { return leaveEarly == null ? Map.of() : leaveEarly; }
        public List<String> reconnect() { return reconnect == null ? List.of() : reconnect; }
        public List<String> absent() { return absent == null ? List.of() : absent; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String speaker, String text) {
    }

    /** actions: [nội dung, người phụ trách, số ngày sau cuộc họp]. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ManualMinutes(String summary, List<String> decisions, List<List<Object>> actions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Board(String key, String title, String owner, String meeting, String color,
                        @com.fasterxml.jackson.annotation.JsonProperty("public") boolean isPublic, String description,
                        List<List<String>> labels, List<List<Object>> shares, List<TaskList> lists) {
        public List<List<String>> labels() { return labels == null ? List.of() : labels; }
        public List<List<Object>> shares() { return shares == null ? List.of() : shares; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskList(String title, List<Card> cards) {
    }

    /**
     * due / start: số ngày so với ngày chạy seed. status: 0 chưa bắt đầu · 1 đang làm · 2 hoàn thành.
     * checklist: [nội dung, đã xong]. comments: [người viết, nội dung, số ngày so với ngày chạy].
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Card(String title, String assignee, Integer due, int status, Integer start, int priority, List<String> labels,
                       List<List<Object>> checklist, List<List<Object>> comments, String description, String attachment) {
        public List<String> labels() { return labels == null ? List.of() : labels; }
        public List<List<Object>> checklist() { return checklist == null ? List.of() : checklist; }
        public List<List<Object>> comments() { return comments == null ? List.of() : comments; }
    }
}
