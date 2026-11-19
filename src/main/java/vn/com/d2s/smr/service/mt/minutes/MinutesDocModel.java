package vn.com.d2s.smr.service.mt.minutes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.service.mt.impl.RoomAdmission;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Dữ liệu có cấu trúc để dựng bố cục biên bản Word mặc định (xem {@link MinutesDocxRenderer#renderDefault}). */
public record MinutesDocModel(
        String company,
        boolean nationalHeader,
        String meetingName,
        String time,
        String chair,
        String secretary,
        List<Person> participants,
        List<String> summaryParagraphs,
        List<Discussion> discussion,
        List<String> decisions,
        List<Action> actions,
        String footerNote
) {
    public record Person(String name, String role) {
    }

    public record Discussion(String topic, String text) {
    }

    /** Một dòng bảng việc cần làm. */
    public record Action(String text, String assignee, String deadline) {
    }

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Cấu hình tên công ty / quốc hiệu cho mẫu mặc định (đặt qua {@code smr.minutes.*}). */
    @Component
    static class Settings {
        Settings(@Value("${smr.minutes.company-name:Công ty cổ phần phần mềm ABS}") String company,
                 @Value("${smr.minutes.national-header:true}") boolean national) {
            MinutesDocModel.defaultCompany = company;
            MinutesDocModel.defaultNational = national;
        }
    }

    private static volatile String defaultCompany = "Công ty cổ phần phần mềm ABS";
    private static volatile boolean defaultNational = true;

    public static MinutesDocModel of(String meetingName, String time, List<MeetingPersonal> people, MinutesContent content, String footerNote) {
        List<Person> participants = new ArrayList<>();
        String chair = "";
        String secretary = "";
        for (MeetingPersonal p : people) {
            if (RoomAdmission.isGuest(p.getUserName())) continue;
            String name = p.getFullName() != null && !p.getFullName().isBlank() ? p.getFullName() : p.getUserName();
            String role = switch (p.getType()) {
                case 1 -> "Chủ trì";
                case 2 -> "Đồng chủ trì";
                case 3 -> "Thư ký";
                default -> "Thành viên";
            };
            if (p.getType() == 1 && chair.isEmpty()) chair = name;
            if (p.getType() == 3 && secretary.isEmpty()) secretary = name;
            participants.add(new Person(name, role));
        }
        List<String> summary = new ArrayList<>();
        for (String part : (content.summary() == null ? "" : content.summary()).split("\\n\\s*\\n|\\n")) {
            if (!part.isBlank()) summary.add(part.trim());
        }
        List<Discussion> discussion = new ArrayList<>();
        for (MinutesContent.Discussion d : content.discussion()) {
            if (d.text() != null && !d.text().isBlank()) discussion.add(new Discussion(d.topic() == null ? "" : d.topic().trim(), d.text().trim()));
        }
        List<String> decisions = content.decisions().stream().map(MinutesContent.Decision::text).toList();
        List<Action> actions = new ArrayList<>();
        for (MinutesContent.ActionItem a : content.actions()) {
            String who = a.assigneeName() != null ? a.assigneeName() : a.assignee() != null ? a.assignee() : "Chưa xác định";
            String due = a.dueDate() != null ? LocalDate.parse(a.dueDate()).format(VN_DATE) : "Chưa xác định";
            actions.add(new Action(a.text(), who, due));
        }
        return new MinutesDocModel(defaultCompany, defaultNational, meetingName, time, chair, secretary, participants, summary, discussion,
                decisions, actions, footerNote);
    }
}
