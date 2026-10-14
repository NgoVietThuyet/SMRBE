package vn.com.d2s.smr.service.rp;

import vn.com.d2s.smr.dto.rp.MeetingReportResponse;
import vn.com.d2s.smr.dto.rp.TaskReportResponse;

import java.time.LocalDate;

public interface ReportService {

    /** organizationId != null → chỉ tính cuộc họp có chủ trì thuộc phòng ban đó hoặc phòng ban con. */
    MeetingReportResponse meetingReport(LocalDate from, LocalDate to, String organizationId);

    TaskReportResponse taskReport(LocalDate from, LocalDate to);
}
