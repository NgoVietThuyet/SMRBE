package vn.com.d2s.smr.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
        List<Account> accounts,
        List<HostAccount> hostAccounts,
        List<Recurring> recurring,
        List<Meeting> meetings,
        Map<String, List<Line>> transcripts,
        Map<String, ManualMinutes> manualMinutes,
        List<Board> boards,
        Map<String, String> meetingTemplates
) {
    public static final String RESOURCE = "/seed/demo-data.json";

    /** Đọc nội dung, các tên giữ chỗ chủ trì ({@code "@thuyet"}…) còn nguyên. */
    public static DemoData load(ObjectMapper mapper) {
        return load(mapper, Map.of());
    }

    /**
     * Đọc nội dung và thay tên giữ chỗ chủ trì bằng tên đăng nhập thật trong DB
     * ({@code aliasToUser}: "@thuyet" → "thuyet.ngo"…). Chỉ thay khi giá trị JSON khớp đúng cả dấu nháy.
     */
    public static DemoData load(ObjectMapper mapper, Map<String, String> aliasToUser) {
        try (InputStream in = DemoData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Không tìm thấy " + RESOURCE);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> e : aliasToUser.entrySet()) {
                json = json.replace(mapper.writeValueAsString(e.getKey()), mapper.writeValueAsString(e.getValue()));
            }
            return mapper.readValue(json, DemoData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + RESOURCE, e);
        }
    }

    /** Tài khoản có sẵn trong DB. {@code alias} là tên giữ chỗ trong JSON; tìm theo họ tên. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HostAccount(String alias, String fullName) {
    }

    /** Phòng ban và chức danh không ghi ở đây: lấy ngẫu nhiên từ DB lúc nạp. Email, mật khẩu theo tên đăng nhập. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String userName, String fullName, String phone,
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
