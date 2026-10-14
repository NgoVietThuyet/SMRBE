package vn.com.d2s.smr.controller.rp;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import vn.com.d2s.smr.dto.rp.AiPipelineReportResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.com.d2s.smr.dto.rp.MeetingReportResponse;
import vn.com.d2s.smr.dto.rp.TaskReportResponse;
import vn.com.d2s.smr.service.rp.ReportService;

import java.time.LocalDate;

/** Thống kê quản trị (P5) — chỉ Admin (SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/meetings")
    public MeetingReportResponse meetings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String organizationId
    ) {
        return reportService.meetingReport(from, to, organizationId);
    }

    @GetMapping("/tasks")
    public TaskReportResponse tasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.taskReport(from, to);
    }

    @GetMapping("/ai-pipeline")
    public AiPipelineReportResponse aiPipeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.aiPipelineReport(from, to);
    }
}
