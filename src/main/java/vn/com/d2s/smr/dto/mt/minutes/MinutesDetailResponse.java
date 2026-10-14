package vn.com.d2s.smr.dto.mt.minutes;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/** Trình soạn biên bản: thông tin cuộc họp + quyền + bản đang xem + danh sách phiên bản + job gần nhất. */
public record MinutesDetailResponse(
        String meetingId,
        String meetingName,
        int meetingStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @JsonProperty("isModerator") boolean moderator,
        @JsonProperty("isSecretary") boolean secretary,
        boolean canGenerate,
        boolean canEdit,
        boolean canApprove,
        MinutesVersionResponse current,
        List<VersionItem> versions,
        MinutesJobResponse job,
        List<Person> participants,
        int transcriptCount
) {
    public record VersionItem(int version, int status, String source, LocalDateTime createdAt) {
    }

    public record Person(String userName, String fullName) {
    }
}
