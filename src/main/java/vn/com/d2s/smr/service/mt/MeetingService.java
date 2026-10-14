package vn.com.d2s.smr.service.mt;

import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.CancelMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.CreateMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDashboardResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDetailResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingJoinInfoResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingListItemResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSearchRequest;
import vn.com.d2s.smr.dto.mt.meeting.QuickMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.ScheduleConflictRequest;
import vn.com.d2s.smr.dto.mt.meeting.ScheduleConflictResponse;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingParticipantsRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingRequest;
import vn.com.d2s.smr.dto.mt.message.MeetingMessageResponse;
import vn.com.d2s.smr.dto.mt.message.SendMeetingMessageRequest;

import vn.com.d2s.smr.dto.mt.meeting.MeetingCodeLookupResponse;

import java.util.List;
import java.util.Optional;

public interface MeetingService {

    MeetingDashboardResponse getDashboard(String userName);

    /** Tra cứu theo mã phòng cho "Tham gia bằng mã". Không ném lỗi khi không thấy — trả reason NOT_FOUND. */
    MeetingCodeLookupResponse lookupByRoomCode(String userName, String roomCode);

    PagedResultResponse<MeetingListItemResponse> searchMeetings(String userName, MeetingSearchRequest request);

    Optional<MeetingDetailResponse> getMeetingDetail(String userName, String meetingId);

    MeetingDetailResponse createMeeting(String userName, CreateMeetingRequest request);

    MeetingDetailResponse updateMeeting(String userName, String meetingId, UpdateMeetingRequest request);

    void deleteMeeting(String userName, String meetingId);

    void cancelMeeting(String userName, String meetingId, CancelMeetingRequest request);

    void archiveMeeting(String userName, String meetingId);

    MeetingDetailResponse createQuickMeeting(String userName, QuickMeetingRequest request);

    MeetingDetailResponse addParticipants(String userName, String meetingId, UpdateMeetingParticipantsRequest request);

    void removeParticipant(String userName, String meetingId, String participantUserName);

    MeetingJoinInfoResponse getJoinInfo(String userName, String meetingId);

    void startMeeting(String userName, String meetingId);

    void endMeeting(String userName, String meetingId);

    void joinMeeting(String userName, String meetingId);

    void leaveMeeting(String userName, String meetingId);

    List<MeetingMessageResponse> getMessages(String userName, String meetingId);

    MeetingMessageResponse sendMessage(String userName, String meetingId, SendMeetingMessageRequest request);

    /** Người (gồm cả mình) bị trùng lịch với cuộc họp khác trong khoảng giờ đã chọn. Chỉ cảnh báo, không chặn. */
    List<ScheduleConflictResponse> findScheduleConflicts(String userName, ScheduleConflictRequest request);
}
