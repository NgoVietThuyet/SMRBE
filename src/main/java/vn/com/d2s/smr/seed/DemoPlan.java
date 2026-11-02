package vn.com.d2s.smr.seed;

import vn.com.d2s.smr.config.BusinessTime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tính lịch cụ thể từ nội dung demo: ngày giờ (giờ VN → UTC), trạng thái, Id cố định.
 * Thuần tính toán, không đụng DB → có unit test.
 */
public final class DemoPlan {

    public static final String ID_PREFIX = "demo_";
    public static final String SEED_USER = "seed";
    private static final Pattern NOW = Pattern.compile("NOW([+-]\\d+)");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\+(\\d+)}");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private DemoPlan() {
    }

    public enum Status {
        DRAFT(0), SCHEDULED(1), ONGOING(2), ENDED(3), CANCELLED(4), ARCHIVED(5);
        public final int code;

        Status(int code) {
            this.code = code;
        }

        static Status of(String s) {
            return switch (s) {
                case "ended" -> ENDED;
                case "ongoing" -> ONGOING;
                case "scheduled" -> SCHEDULED;
                case "cancelled" -> CANCELLED;
                case "draft" -> DRAFT;
                case "archived" -> ARCHIVED;
                default -> throw new IllegalArgumentException("Trạng thái cuộc họp không hợp lệ: " + s);
            };
        }
    }

    /** Một cuộc họp cụ thể. startUtc / endUtc: giờ dự kiến (UTC). */
    public record PlannedMeeting(String id, DemoData.Meeting source, Status status, LocalDateTime startUtc, LocalDateTime endUtc,
                                 LocalDate localDate) {
        /** userName → vai trò (1 chủ trì · 2 đồng chủ trì · 3 thư ký · 4 bắt buộc · 5 tùy chọn), giữ thứ tự. */
        public Map<String, Integer> members() {
            Map<String, Integer> roles = new LinkedHashMap<>();
            roles.put(source.host(), 1);
            source.cohost().forEach(u -> roles.putIfAbsent(u, 2));
            if (source.secretary() != null) roles.putIfAbsent(source.secretary(), 3);
            source.required().forEach(u -> roles.putIfAbsent(u, 4));
            source.optional().forEach(u -> roles.putIfAbsent(u, 5));
            return roles;
        }
    }

    /** Cuộc họp lẻ + các buổi của cuộc họp định kỳ, theo ngày chạy {@code nowUtc}. */
    public static List<PlannedMeeting> plan(DemoData data, LocalDateTime nowUtc) {
        ZonedDateTime nowVn = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE);
        LocalDate today = nowVn.toLocalDate();
        List<PlannedMeeting> out = new ArrayList<>();
        for (DemoData.Recurring r : data.recurring()) {
            LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(r.weekday())));
            for (int w = -r.weeksBefore(); w <= r.weeksAfter(); w++) {
                LocalDate date = thisWeek.plusWeeks(w);
                LocalDateTime start = toUtc(date, LocalTime.parse(r.start()));
                LocalDateTime end = start.plusMinutes(r.minutes());
                Status status = end.isBefore(nowUtc) ? Status.ENDED : start.isAfter(nowUtc) ? Status.SCHEDULED : Status.ONGOING;
                String key = r.key() + "_" + date.toString().replace("-", "");
                DemoData.Meeting m = new DemoData.Meeting(key, r.name() + " " + date.format(DD_MM), 0, r.start(), r.minutes(), r.host(),
                        status.name().toLowerCase(), r.cohost(), r.secretary(), r.required(), r.optional(), r.description(), r.agenda(),
                        r.visibility(), false, false, null, null, null, null, List.of(), null);
                out.add(new PlannedMeeting(meetingId(key), m, status, start, end, date));
            }
        }
        for (DemoData.Meeting m : data.meetings()) {
            LocalDateTime start;
            Matcher now = NOW.matcher(m.start());
            if (now.matches()) {
                start = nowUtc.plusMinutes(Integer.parseInt(now.group(1))).withSecond(0).withNano(0);
            } else {
                start = toUtc(today.plusDays(m.day()), LocalTime.parse(m.start()));
            }
            LocalDate local = start.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE).toLocalDate();
            out.add(new PlannedMeeting(meetingId(m.key()), m, Status.of(m.status()), start, start.plusMinutes(m.minutes()), local));
        }
        return out;
    }

    public static LocalDateTime toUtc(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(BusinessTime.ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Ngày (so với hôm nay, giờ VN) lúc {@code hour} giờ VN → UTC. */
    public static LocalDateTime dayAt(LocalDateTime nowUtc, int dayOffset, int hour) {
        LocalDate today = nowUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE).toLocalDate();
        return toUtc(today.plusDays(dayOffset), LocalTime.of(hour, 0));
    }

    /** "{+5}" trong transcript → ngày họp + 5 ngày, dạng dd/MM. */
    public static String fillDates(String text, LocalDate meetingDate) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, meetingDate.plusDays(Integer.parseInt(m.group(1))).format(DD_MM));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String meetingId(String key) {
        return ID_PREFIX + "m_" + key;
    }

    /** Id cố định ≤ 40 ký tự từ một chuỗi khóa → chạy lại không sinh trùng. */
    public static String id(String kind, String key) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((kind + ":" + key).getBytes(StandardCharsets.UTF_8));
            return ID_PREFIX + kind + "_" + HexFormat.of().formatHex(h).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mã phòng cố định 8 ký tự, bắt đầu bằng "d" để dễ nhận ra dữ liệu demo. */
    public static String roomCode(String meetingKey) {
        return "d" + id("room", meetingKey).substring(ID_PREFIX.length() + 5, ID_PREFIX.length() + 12);
    }
}
