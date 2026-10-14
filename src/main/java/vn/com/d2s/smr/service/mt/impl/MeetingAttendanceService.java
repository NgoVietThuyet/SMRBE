package vn.com.d2s.smr.service.mt.impl;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.config.BusinessTime;
import vn.com.d2s.smr.dto.mt.room.AttendanceSummaryResponse;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MtAttendanceEvent;
import vn.com.d2s.smr.repository.mt.MtAttendanceEventRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lịch sử vào/rời phòng (MtAttendanceEvents) và tính thời lượng tham dự.
 *
 * <p>Mỗi kết nối (ClientSessionId) tạo một khoảng [vào, rời). Một người mở nhiều tab / vào lại nhiều lần
 * thì các khoảng được <b>hợp lại</b> trước khi cộng, nên không bị tính trùng. Khoảng chưa có "rời"
 * (mất kết nối, cuộc họp vẫn đang diễn ra) được tính tới lúc kết thúc cuộc họp hoặc tới hiện tại.</p>
 */
@Service
public class MeetingAttendanceService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final MtAttendanceEventRepository repository;
    private final Clock clock;

    @Autowired
    public MeetingAttendanceService(MtAttendanceEventRepository repository) {
        this(repository, Clock.systemUTC());
    }

    MeetingAttendanceService(MtAttendanceEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(String meetingId, String userName, int eventType, String clientSessionId) {
        if (meetingId == null || userName == null) {
            return;
        }
        MtAttendanceEvent e = new MtAttendanceEvent();
        e.setId(UUID.randomUUID().toString().replace("-", ""));
        e.setMeetingId(meetingId);
        e.setUserName(userName);
        e.setEventType(eventType);
        e.setOccurredAt(LocalDateTime.now(clock));
        e.setClientSessionId(clientSessionId == null || clientSessionId.isBlank() ? "-" : clientSessionId);
        repository.save(e);
    }

    /** Thời lượng tham dự của từng thành viên (cả người chưa từng vào — 0 giây). Thời gian trả về theo UTC. */
    @Transactional(readOnly = true)
    public List<AttendanceSummaryResponse> summarize(MeetingInfo meeting, List<MeetingPersonal> personals) {
        LocalDateTime cap = meeting.getEndDate() != null ? meeting.getEndDate() : LocalDateTime.now(clock);
        List<MtAttendanceEvent> events = repository.findByMeetingIdOrderByOccurredAtAsc(meeting.getId());
        Map<String, List<MtAttendanceEvent>> byUser = new HashMap<>();
        for (MtAttendanceEvent e : events) {
            byUser.computeIfAbsent(e.getUserName().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(e);
        }

        List<AttendanceSummaryResponse> result = new ArrayList<>();
        for (MeetingPersonal p : personals) {
            List<MtAttendanceEvent> mine = byUser.getOrDefault(p.getUserName().toLowerCase(Locale.ROOT), List.of());
            List<LocalDateTime[]> intervals = intervals(mine, cap);
            List<LocalDateTime[]> merged = merge(intervals);
            long seconds = merged.stream().mapToLong(i -> Duration.between(i[0], i[1]).getSeconds()).sum();
            int joins = (int) mine.stream().filter(e -> e.getEventType() == MtAttendanceEvent.ENTER).count();
            LocalDateTime first = merged.isEmpty() ? null : merged.get(0)[0];
            LocalDateTime last = merged.isEmpty() ? null : merged.get(merged.size() - 1)[1];
            result.add(new AttendanceSummaryResponse(
                    p.getUserName(), p.getFullName(), p.getType(), RoomAdmission.isGuest(p.getUserName()),
                    first, last, joins, seconds));
        }
        result.sort(Comparator.comparingInt(AttendanceSummaryResponse::role)
                .thenComparing(AttendanceSummaryResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    /** Ghép ENTER/LEAVE theo ClientSessionId thành các khoảng [vào, rời). */
    static List<LocalDateTime[]> intervals(List<MtAttendanceEvent> events, LocalDateTime cap) {
        Map<String, LocalDateTime> open = new LinkedHashMap<>();
        List<LocalDateTime[]> out = new ArrayList<>();
        for (MtAttendanceEvent e : events) {
            String sid = e.getClientSessionId();
            if (e.getEventType() == MtAttendanceEvent.ENTER) {
                // Vào lại trên cùng kết nối mà chưa có "rời" → đóng khoảng cũ tại thời điểm này.
                LocalDateTime prev = open.put(sid, e.getOccurredAt());
                if (prev != null) addInterval(out, prev, e.getOccurredAt(), cap);
            } else {
                LocalDateTime start = open.remove(sid);
                if (start != null) addInterval(out, start, e.getOccurredAt(), cap);
            }
        }
        open.values().forEach(start -> addInterval(out, start, cap, cap));
        return out;
    }

    private static void addInterval(List<LocalDateTime[]> out, LocalDateTime start, LocalDateTime end, LocalDateTime cap) {
        LocalDateTime e = end.isAfter(cap) ? cap : end;
        if (e.isAfter(start)) out.add(new LocalDateTime[]{start, e});
    }

    /** Hợp các khoảng chồng nhau (nhiều tab, vào lại khi kết nối cũ chưa kịp báo rời). */
    static List<LocalDateTime[]> merge(List<LocalDateTime[]> intervals) {
        List<LocalDateTime[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(i -> i[0]));
        List<LocalDateTime[]> merged = new ArrayList<>();
        for (LocalDateTime[] i : sorted) {
            if (!merged.isEmpty() && !i[0].isAfter(merged.get(merged.size() - 1)[1])) {
                LocalDateTime[] last = merged.get(merged.size() - 1);
                if (i[1].isAfter(last[1])) last[1] = i[1];
            } else {
                merged.add(new LocalDateTime[]{i[0], i[1]});
            }
        }
        return merged;
    }

    /** File XLSX tham dự; giờ hiển thị theo giờ Việt Nam. */
    public byte[] exportXlsx(MeetingInfo meeting, List<AttendanceSummaryResponse> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Tham dự");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle title = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("DANH SÁCH THAM DỰ: " + meeting.getName());
            r0.getCell(0).setCellStyle(title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Bắt đầu: " + fmt(meeting.getStartDate() != null ? meeting.getStartDate() : meeting.getExpectedStartTime())
                    + "   ·   Kết thúc: " + (meeting.getEndDate() != null ? fmt(meeting.getEndDate()) : "chưa kết thúc")
                    + "   ·   Giờ Việt Nam (GMT+7)");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

            String[] cols = {"STT", "Họ tên", "Tài khoản", "Vai trò", "Vào lần đầu", "Rời lần cuối", "Số lần vào", "Thời lượng (phút)"};
            Row h = sheet.createRow(3);
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
                h.getCell(i).setCellStyle(header);
            }
            int rowIdx = 4;
            for (AttendanceSummaryResponse a : rows) {
                Row r = sheet.createRow(rowIdx);
                r.createCell(0).setCellValue(rowIdx - 3);
                r.createCell(1).setCellValue(a.fullName());
                r.createCell(2).setCellValue(a.guest() ? "(khách)" : a.userName());
                r.createCell(3).setCellValue(a.guest() ? "Khách" : roleName(a.role()));
                r.createCell(4).setCellValue(a.firstJoinAt() == null ? "" : fmt(a.firstJoinAt()));
                r.createCell(5).setCellValue(a.lastLeaveAt() == null ? "" : fmt(a.lastLeaveAt()));
                r.createCell(6).setCellValue(a.joinCount());
                r.createCell(7).setCellValue(Math.round(a.totalSeconds() / 60.0 * 10) / 10.0);
                rowIdx++;
            }
            int[] widths = {6, 28, 22, 14, 22, 22, 12, 18};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0, 4);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fmt(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BusinessTime.ZONE).format(TIME);
    }

    private static String roleName(int role) {
        return switch (role) {
            case 1 -> "Chủ trì";
            case 2 -> "Đồng chủ trì";
            case 3 -> "Thư ký";
            case 4 -> "Bắt buộc";
            case 5 -> "Tùy chọn";
            default -> "Thành viên";
        };
    }
}
