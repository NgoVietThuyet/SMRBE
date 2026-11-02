package vn.com.d2s.smr.service.rp.impl;

import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.dto.rp.AiPipelineReportResponse;
import vn.com.d2s.smr.dto.rp.MeetingReportResponse;
import vn.com.d2s.smr.dto.rp.TaskReportResponse;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.entity.sy.SyBackgroundJob;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    @Test
    void aggregatesMeetingsByStatusWeekAndHostOrganization() {
        List<MdOrganize> orgs = List.of(org("ROOT", ""), org("DEV", "ROOT"), org("HR", ""));
        List<MeetingInfo> meetings = List.of(
                // Thứ Hai 05/10: họp 1.5 giờ, đã kết thúc
                meeting("M1", 3, LocalDateTime.of(2026, 10, 5, 9, 0), LocalDateTime.of(2026, 10, 5, 9, 0), LocalDateTime.of(2026, 10, 5, 10, 30)),
                // Thứ Tư cùng tuần: đã hủy
                meeting("M2", 4, LocalDateTime.of(2026, 10, 7, 14, 0), null, null),
                // Tuần sau: sắp diễn ra, chủ trì thuộc HR
                meeting("M3", 1, LocalDateTime.of(2026, 10, 13, 8, 0), null, null)
        );
        List<MeetingPersonal> people = List.of(
                person("M1", "an", "DEV", 1, true),
                person("M1", "binh", "DEV", 2, true),
                person("M2", "an", "DEV", 1, false),
                person("M3", "chi", "HR", 1, false),
                person("M3", "binh", "DEV", 2, false)
        );

        MeetingReportResponse report = ReportServiceImpl.buildMeetingReport(FROM, TO, null, meetings, people, orgs);

        assertThat(report.meetingCount()).isEqualTo(3);
        assertThat(report.endedCount()).isEqualTo(1);
        assertThat(report.cancelledCount()).isEqualTo(1);
        assertThat(report.upcomingCount()).isEqualTo(1);
        assertThat(report.totalHours()).isEqualTo(1.5);
        assertThat(report.participantCount()).isEqualTo(3);
        assertThat(report.attendedCount()).isEqualTo(2);
        assertThat(report.byWeek()).hasSize(2);
        assertThat(report.byWeek().get(0).weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(report.byWeek().get(0).meetingCount()).isEqualTo(2);
        assertThat(report.byOrganization()).extracting(MeetingReportResponse.OrganizationStat::organizationId)
                .containsExactly("DEV", "HR");
    }

    @Test
    void organizationFilterIncludesChildOrganizations() {
        List<MdOrganize> orgs = List.of(org("ROOT", ""), org("DEV", "ROOT"), org("HR", ""));
        List<MeetingInfo> meetings = List.of(
                meeting("M1", 1, LocalDateTime.of(2026, 10, 5, 9, 0), null, null),
                meeting("M2", 1, LocalDateTime.of(2026, 10, 6, 9, 0), null, null)
        );
        List<MeetingPersonal> people = List.of(person("M1", "an", "DEV", 1, false), person("M2", "chi", "HR", 1, false));

        MeetingReportResponse report = ReportServiceImpl.buildMeetingReport(FROM, TO, "ROOT", meetings, people, orgs);

        assertThat(report.meetingCount()).isEqualTo(1);
        assertThat(report.byOrganization()).singleElement()
                .extracting(MeetingReportResponse.OrganizationStat::organizationName).isEqualTo("Org DEV");
    }

    @Test
    void countsTasksByStatusAndOverdue() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);
        List<MeetingTask> tasks = List.of(
                task(0, now.minusDays(1)), // quá hạn
                task(1, now.plusDays(1)),
                task(2, now.minusDays(3)), // hoàn thành → không tính quá hạn
                task(1, null)
        );

        TaskReportResponse report = ReportServiceImpl.buildTaskReport(FROM, TO, tasks, now);

        assertThat(report.total()).isEqualTo(4);
        assertThat(report.notStarted()).isEqualTo(1);
        assertThat(report.inProgress()).isEqualTo(2);
        assertThat(report.completed()).isEqualTo(1);
        assertThat(report.overdue()).isEqualTo(1);
    }

    private static MdOrganize org(String id, String parentId) {
        MdOrganize o = new MdOrganize();
        o.setId(id);
        o.setParentId(parentId);
        o.setName("Org " + id);
        return o;
    }

    private static MeetingInfo meeting(String id, int status, LocalDateTime expectedStart, LocalDateTime start, LocalDateTime end) {
        MeetingInfo m = new MeetingInfo();
        m.setId(id);
        m.setStatus(status);
        m.setExpectedStartTime(expectedStart);
        m.setStartDate(start);
        m.setEndDate(end);
        return m;
    }

    private static MeetingPersonal person(String meetingId, String userName, String orgId, int type, boolean joined) {
        MeetingPersonal p = new MeetingPersonal();
        p.setMeetingId(meetingId);
        p.setUserName(userName);
        p.setOrgId(orgId);
        p.setType(type);
        p.setChairperson(type == 1);
        p.setJoined(joined);
        return p;
    }

    private static MeetingTask task(int status, LocalDateTime dueDate) {
        MeetingTask t = new MeetingTask();
        t.setStatus(status);
        t.setDueDate(dueDate);
        return t;
    }

    private static SyBackgroundJob job(String id, String type, int status, LocalDateTime created, Integer waitSec, Integer runSec) {
        SyBackgroundJob j = new SyBackgroundJob();
        j.setId(id);
        j.setType(type);
        j.setStatus(status);
        j.setRefId("m1");
        j.setCreatedBy("u1");
        j.setCreatedAt(created);
        if (waitSec != null) j.setStartedAt(created.plusSeconds(waitSec));
        if (waitSec != null && runSec != null) j.setFinishedAt(created.plusSeconds(waitSec + runSec));
        j.setError(status == SyBackgroundJob.FAILED ? "Không kết nối được máy chủ AI." : null);
        return j;
    }

    @Test
    void aiPipelineReportCountsStatusesDurationsAndFailures() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 10, 8, 0);
        List<SyBackgroundJob> jobs = List.of(
                job("a", "MINUTES_GENERATE", SyBackgroundJob.DONE, t, 2, 10),
                job("b", "MINUTES_GENERATE", SyBackgroundJob.DONE, t.plusHours(1), 4, 30),
                job("c", "MINUTES_EXPORT", SyBackgroundJob.FAILED, t.plusHours(2), 0, 5),
                job("d", "MINUTES_EXPORT", SyBackgroundJob.FAILED, t.plusHours(3), 0, 5),
                job("e", "MINUTES_EXPORT", SyBackgroundJob.RUNNING, t.plusHours(4), 6, null),
                job("f", "MINUTES_EXPORT", SyBackgroundJob.PENDING, t.plusHours(5), null, null));

        AiPipelineReportResponse r = ReportServiceImpl.buildAiPipelineReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14),
                jobs, Map.of("m1", "Họp giao ban"));

        assertThat(r.total()).isEqualTo(6);
        assertThat(r.done()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(2);
        assertThat(r.running()).isEqualTo(1);
        assertThat(r.pending()).isEqualTo(1);
        assertThat(r.successRate()).isEqualTo(50.0);
        // Chờ: (2 + 4 + 0 + 0 + 6) / 5 job đã bắt đầu; chạy: chỉ tính job xong (10, 30).
        assertThat(r.avgWaitSeconds()).isEqualTo(2.4);
        assertThat(r.avgRunSeconds()).isEqualTo(20.0);
        assertThat(r.p95RunSeconds()).isEqualTo(30.0);
        assertThat(r.byType()).extracting(AiPipelineReportResponse.TypeStat::label)
                .containsExactly("Tổng hợp biên bản theo mẫu", "Sinh biên bản (AI)");
        assertThat(r.byType().get(0).failed()).isEqualTo(2);
        assertThat(r.byType().get(1).avgRunSeconds()).isEqualTo(20.0);
        // Lỗi mới nhất lên đầu, kèm tên cuộc họp.
        assertThat(r.recentFailures()).extracting(AiPipelineReportResponse.Failure::id).containsExactly("d", "c");
        assertThat(r.recentFailures().get(0).meetingName()).isEqualTo("Họp giao ban");
    }

    @Test
    void aiPipelineReportEmptyRange() {
        AiPipelineReportResponse r = ReportServiceImpl.buildAiPipelineReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), List.of(), Map.of());
        assertThat(r.total()).isZero();
        assertThat(r.successRate()).isZero();
        assertThat(r.p95RunSeconds()).isZero();
        assertThat(r.byType()).isEmpty();
    }
}
