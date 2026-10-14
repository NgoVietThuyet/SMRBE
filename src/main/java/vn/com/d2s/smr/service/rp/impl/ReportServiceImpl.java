package vn.com.d2s.smr.service.rp.impl;

import vn.com.d2s.smr.dto.rp.AiPipelineReportResponse;
import vn.com.d2s.smr.entity.sy.SyBackgroundJob;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.d2s.smr.dto.rp.MeetingReportResponse;
import vn.com.d2s.smr.dto.rp.MeetingReportResponse.OrganizationStat;
import vn.com.d2s.smr.dto.rp.MeetingReportResponse.WeekStat;
import vn.com.d2s.smr.dto.rp.TaskReportResponse;
import vn.com.d2s.smr.entity.md.MdOrganize;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MeetingPersonal;
import vn.com.d2s.smr.entity.mt.MeetingTask;
import vn.com.d2s.smr.service.rp.ReportService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Báo cáo quản trị (P5). Dữ liệu khóa luận nhỏ nên lấy theo khoảng ngày rồi tổng hợp trong bộ nhớ;
 * phần tính toán tách thành hàm tĩnh để test không cần DB.
 */
@Service
public class ReportServiceImpl implements ReportService {

    static final int STATUS_DRAFT = 0;
    static final int STATUS_SCHEDULED = 1;
    static final int STATUS_ONGOING = 2;
    static final int STATUS_ENDED = 3;
    static final int STATUS_CANCELLED = 4;
    static final int STATUS_ARCHIVED = 5;
    static final int ROLE_HOST = 1;
    static final int TASK_COMPLETED = 2;

    private final EntityManager entityManager;
    private final Clock clock;

    public ReportServiceImpl(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingReportResponse meetingReport(LocalDate from, LocalDate to, String organizationId) {
        LocalDate[] range = normalizeRange(from, to);
        List<MeetingInfo> meetings = entityManager.createQuery("""
                        select m from MeetingInfo m
                        where m.deleted = false and m.expectedStartTime >= :from and m.expectedStartTime < :to
                        """, MeetingInfo.class)
                .setParameter("from", range[0].atStartOfDay())
                .setParameter("to", range[1].plusDays(1).atStartOfDay())
                .getResultList();

        List<MeetingPersonal> participants = meetings.isEmpty() ? List.of() : entityManager.createQuery("""
                        select p from MeetingPersonal p where p.meetingId in :ids
                        """, MeetingPersonal.class)
                .setParameter("ids", meetings.stream().map(MeetingInfo::getId).toList())
                .getResultList();

        List<MdOrganize> organizations = entityManager
                .createQuery("select o from MdOrganize o", MdOrganize.class)
                .getResultList();

        return buildMeetingReport(range[0], range[1], organizationId, meetings, participants, organizations);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskReportResponse taskReport(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        List<MeetingTask> tasks = entityManager.createQuery("""
                        select t from MeetingTask t where t.createDate >= :from and t.createDate < :to
                        """, MeetingTask.class)
                .setParameter("from", range[0].atStartOfDay())
                .setParameter("to", range[1].plusDays(1).atStartOfDay())
                .getResultList();
        return buildTaskReport(range[0], range[1], tasks, LocalDateTime.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public AiPipelineReportResponse aiPipelineReport(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        List<SyBackgroundJob> jobs = entityManager.createQuery("""
                        select j from SyBackgroundJob j where j.createdAt >= :from and j.createdAt < :to
                        """, SyBackgroundJob.class)
                .setParameter("from", range[0].atStartOfDay())
                .setParameter("to", range[1].plusDays(1).atStartOfDay())
                .getResultList();
        Set<String> meetingIds = jobs.stream().filter(j -> j.getStatus() == SyBackgroundJob.FAILED)
                .map(SyBackgroundJob::getRefId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = meetingIds.isEmpty() ? Map.of() : entityManager.createQuery(
                        "select m from MeetingInfo m where m.id in :ids", MeetingInfo.class)
                .setParameter("ids", meetingIds).getResultList().stream()
                .collect(Collectors.toMap(MeetingInfo::getId, MeetingInfo::getName, (a, b) -> a));
        return buildAiPipelineReport(range[0], range[1], jobs, names);
    }

    static String jobLabel(String type) {
        return switch (type == null ? "" : type) {
            case "MINUTES_GENERATE" -> "Sinh biên bản (AI)";
            case "MINUTES_EXPORT" -> "Tổng hợp biên bản theo mẫu";
            default -> type == null || type.isBlank() ? "Khác" : type;
        };
    }

    static AiPipelineReportResponse buildAiPipelineReport(LocalDate from, LocalDate to, List<SyBackgroundJob> jobs,
                                                          Map<String, String> meetingNames) {
        int pending = 0;
        int running = 0;
        int done = 0;
        int failed = 0;
        List<Double> waits = new ArrayList<>();
        List<Double> runs = new ArrayList<>();
        for (SyBackgroundJob j : jobs) {
            switch (j.getStatus()) {
                case SyBackgroundJob.PENDING -> pending++;
                case SyBackgroundJob.RUNNING -> running++;
                case SyBackgroundJob.DONE -> done++;
                default -> failed++;
            }
            if (j.getStartedAt() != null && j.getCreatedAt() != null) waits.add(seconds(j.getCreatedAt(), j.getStartedAt()));
            if (finished(j)) runs.add(seconds(j.getStartedAt(), j.getFinishedAt()));
        }
        Map<String, List<SyBackgroundJob>> byType = jobs.stream()
                .collect(Collectors.groupingBy(j -> j.getType() == null ? "" : j.getType(), TreeMap::new, Collectors.toList()));
        List<AiPipelineReportResponse.TypeStat> types = byType.entrySet().stream().map(e -> {
            List<SyBackgroundJob> list = e.getValue();
            int d = (int) list.stream().filter(j -> j.getStatus() == SyBackgroundJob.DONE).count();
            int f = (int) list.stream().filter(j -> j.getStatus() == SyBackgroundJob.FAILED).count();
            double avg = list.stream().filter(ReportServiceImpl::finished)
                    .mapToDouble(j -> seconds(j.getStartedAt(), j.getFinishedAt())).average().orElse(0);
            return new AiPipelineReportResponse.TypeStat(e.getKey(), jobLabel(e.getKey()), list.size(), d, f, round1(avg));
        }).toList();
        List<AiPipelineReportResponse.Failure> failures = jobs.stream()
                .filter(j -> j.getStatus() == SyBackgroundJob.FAILED)
                .sorted(Comparator.comparing(SyBackgroundJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(j -> new AiPipelineReportResponse.Failure(j.getId(), j.getType(), jobLabel(j.getType()), j.getRefId(),
                        meetingNames.getOrDefault(j.getRefId(), ""), j.getError(), j.getCreatedBy(), j.getCreatedAt()))
                .toList();
        double rate = done + failed == 0 ? 0 : round1(100.0 * done / (done + failed));
        return new AiPipelineReportResponse(from, to, jobs.size(), pending, running, done, failed, rate,
                round1(waits.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                round1(runs.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                round1(percentile(runs, 95)), types, failures);
    }

    private static boolean finished(SyBackgroundJob j) {
        return j.getStatus() == SyBackgroundJob.DONE && j.getStartedAt() != null && j.getFinishedAt() != null;
    }

    private static double seconds(LocalDateTime a, LocalDateTime b) {
        return Math.max(0, Duration.between(a, b).toMillis() / 1000.0);
    }

    /** Phân vị theo cách "nearest rank". */
    static double percentile(List<Double> values, int p) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    static MeetingReportResponse buildMeetingReport(
            LocalDate from, LocalDate to, String organizationId,
            List<MeetingInfo> meetings, List<MeetingPersonal> participants, List<MdOrganize> organizations
    ) {
        Map<String, List<MeetingPersonal>> byMeeting = participants.stream()
                .collect(Collectors.groupingBy(MeetingPersonal::getMeetingId));
        Map<String, String> orgNames = organizations.stream()
                .collect(Collectors.toMap(MdOrganize::getId, MdOrganize::getName, (a, b) -> a));

        Set<String> orgScope = organizationId == null || organizationId.isBlank()
                ? null : descendantsOf(organizationId.trim(), organizations);

        List<MeetingInfo> scoped = new ArrayList<>();
        Map<String, String> hostOrg = new HashMap<>();
        for (MeetingInfo m : meetings) {
            String org = byMeeting.getOrDefault(m.getId(), List.of()).stream()
                    .filter(p -> p.getType() == ROLE_HOST || p.isChairperson())
                    .map(MeetingPersonal::getOrgId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("");
            if (orgScope != null && !orgScope.contains(org)) {
                continue;
            }
            hostOrg.put(m.getId(), org);
            scoped.add(m);
        }

        int draft = 0, upcoming = 0, ongoing = 0, ended = 0, cancelled = 0;
        double totalHours = 0;
        Set<String> invited = new HashSet<>();
        Set<String> attended = new HashSet<>();
        Map<LocalDate, double[]> weeks = new TreeMap<>();
        Map<String, double[]> orgStats = new HashMap<>();
        Map<String, Set<String>> orgPeople = new HashMap<>();

        for (MeetingInfo m : scoped) {
            switch (m.getStatus()) {
                case STATUS_DRAFT -> draft++;
                case STATUS_SCHEDULED -> upcoming++;
                case STATUS_ONGOING -> ongoing++;
                case STATUS_ENDED -> ended++;
                case STATUS_CANCELLED -> cancelled++;
                case STATUS_ARCHIVED -> {
                    if (m.getEndDate() != null) ended++; else cancelled++;
                }
                default -> { }
            }
            double hours = actualHours(m);
            totalHours += hours;

            LocalDate weekStart = m.getExpectedStartTime().toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            double[] week = weeks.computeIfAbsent(weekStart, k -> new double[2]);
            week[0]++;
            week[1] += hours;

            String org = hostOrg.get(m.getId());
            double[] stat = orgStats.computeIfAbsent(org, k -> new double[2]);
            stat[0]++;
            stat[1] += hours;

            for (MeetingPersonal p : byMeeting.getOrDefault(m.getId(), List.of())) {
                invited.add(p.getUserName());
                orgPeople.computeIfAbsent(org, k -> new HashSet<>()).add(p.getUserName());
                if (p.isJoined() || p.getJoinTime() != null) {
                    attended.add(p.getUserName());
                }
            }
        }

        List<WeekStat> byWeek = weeks.entrySet().stream()
                .map(e -> new WeekStat(e.getKey(), (int) e.getValue()[0], round1(e.getValue()[1])))
                .toList();
        List<OrganizationStat> byOrganization = orgStats.entrySet().stream()
                .map(e -> new OrganizationStat(
                        e.getKey().isEmpty() ? null : e.getKey(),
                        e.getKey().isEmpty() ? "Chưa xác định" : orgNames.getOrDefault(e.getKey(), e.getKey()),
                        (int) e.getValue()[0],
                        round1(e.getValue()[1]),
                        orgPeople.getOrDefault(e.getKey(), Set.of()).size()))
                .sorted(Comparator.comparingInt(OrganizationStat::meetingCount).reversed()
                        .thenComparing(OrganizationStat::organizationName))
                .toList();

        return new MeetingReportResponse(
                from, to, scoped.size(), draft, upcoming, ongoing, ended, cancelled, round1(totalHours),
                invited.size(), attended.size(), byWeek, byOrganization
        );
    }

    static TaskReportResponse buildTaskReport(LocalDate from, LocalDate to, List<MeetingTask> tasks, LocalDateTime now) {
        int notStarted = 0, inProgress = 0, completed = 0, overdue = 0;
        for (MeetingTask t : tasks) {
            switch (t.getStatus()) {
                case 0 -> notStarted++;
                case 1 -> inProgress++;
                case TASK_COMPLETED -> completed++;
                default -> { }
            }
            if (t.getStatus() != TASK_COMPLETED && t.getDueDate() != null && t.getDueDate().isBefore(now)) {
                overdue++;
            }
        }
        return new TaskReportResponse(from, to, tasks.size(), notStarted, inProgress, completed, overdue);
    }

    /** Mặc định 30 ngày gần nhất; đảo lại nếu from > to; giới hạn tối đa 366 ngày. */
    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        if (start.plusDays(366).isBefore(end)) {
            throw new IllegalArgumentException("Khoảng thời gian thống kê tối đa 1 năm.");
        }
        return new LocalDate[]{start, end};
    }

    private static Set<String> descendantsOf(String rootId, List<MdOrganize> organizations) {
        Map<String, List<String>> children = organizations.stream()
                .collect(Collectors.groupingBy(o -> o.getParentId() == null ? "" : o.getParentId(),
                        Collectors.mapping(MdOrganize::getId, Collectors.toList())));
        Set<String> result = new HashSet<>();
        List<String> queue = new ArrayList<>(List.of(rootId));
        while (!queue.isEmpty()) {
            String id = queue.remove(queue.size() - 1);
            if (result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    private static double actualHours(MeetingInfo m) {
        if (m.getStartDate() == null || m.getEndDate() == null || m.getEndDate().isBefore(m.getStartDate())) {
            return 0;
        }
        return Duration.between(m.getStartDate(), m.getEndDate()).toMinutes() / 60.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
