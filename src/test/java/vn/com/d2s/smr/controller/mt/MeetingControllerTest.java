package vn.com.d2s.smr.controller.mt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.com.d2s.smr.controller.common.GlobalExceptionHandler;
import vn.com.d2s.smr.dto.common.PagedResultResponse;
import vn.com.d2s.smr.dto.mt.meeting.CancelMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.CreateMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDashboardResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingDetailResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingJoinInfoResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingListItemResponse;
import vn.com.d2s.smr.dto.mt.meeting.MeetingSearchRequest;
import vn.com.d2s.smr.dto.mt.meeting.QuickMeetingRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingParticipantsRequest;
import vn.com.d2s.smr.dto.mt.meeting.UpdateMeetingRequest;
import vn.com.d2s.smr.dto.mt.message.MeetingMessageResponse;
import vn.com.d2s.smr.dto.mt.message.SendMeetingMessageRequest;
import vn.com.d2s.smr.service.mt.MeetingService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeetingControllerTest {

    @Mock
    private MeetingService meetingService;

    @Mock
    private vn.com.d2s.smr.service.mt.MeetingTranscriptService transcriptService;

    @Mock
    private vn.com.d2s.smr.service.mt.MeetingRagService ragService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(new MeetingController(meetingService, transcriptService, ragService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDashboardReturns200WithEnvelope() throws Exception {
        when(meetingService.getDashboard("user1")).thenReturn(new MeetingDashboardResponse(2, 1, 3, 0, List.of()));

        mockMvc.perform(get("/api/Meeting/dashboard").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.upcoming").value(2))
                .andExpect(jsonPath("$.data.ongoing").value(1))
                .andExpect(jsonPath("$.data.ended").value(3));
    }

    @Test
    void searchMeetingsReturns200WithPagedResult() throws Exception {
        MeetingListItemResponse item = sampleItem("m1");
        PagedResultResponse<MeetingListItemResponse> paged = PagedResultResponse.of(List.of(item), 1, 10, 1);
        when(meetingService.searchMeetings(eq("user1"), any())).thenReturn(paged);

        MeetingSearchRequest req = new MeetingSearchRequest("all", "", null, null, 1, 10, null, null);

        mockMvc.perform(post("/api/Meeting/query")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value("m1"));
    }

    @Test
    void getMeetingDetailReturns200() throws Exception {
        MeetingDetailResponse detail = sampleDetail("m1");
        when(meetingService.getMeetingDetail("user1", "m1")).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/api/Meeting/m1").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.id").value("m1"))
                .andExpect(jsonPath("$.data.isHost").value(true));
    }

    @Test
    void getMeetingDetailReturns404WhenNotFound() throws Exception {
        when(meetingService.getMeetingDetail("user1", "missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/Meeting/missing").principal(() -> "user1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy cuộc họp."));
    }

    @Test
    void createMeetingReturns200WithCreatedData() throws Exception {
        CreateMeetingRequest req = new CreateMeetingRequest(
                "Cuộc họp A", "Mô tả", "Agenda",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                "Asia/Bangkok", 1, false, true, null, List.of(), List.of()
        );
        MeetingDetailResponse detail = sampleDetail("m1");
        when(meetingService.createMeeting(eq("user1"), any())).thenReturn(detail);

        mockMvc.perform(post("/api/Meeting")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.id").value("m1"))
                .andExpect(jsonPath("$.message").value("Tạo cuộc họp thành công."));
    }

    @Test
    void updateMeetingReturns200() throws Exception {
        UpdateMeetingRequest req = new UpdateMeetingRequest(
                "m1", "Tên mới", "Mô tả mới", "Agenda mới",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                "Asia/Bangkok", 1, null, "0x0000000000000001", null, null
        );
        MeetingDetailResponse detail = sampleDetail("m1");
        when(meetingService.updateMeeting(eq("user1"), eq("m1"), any())).thenReturn(detail);

        mockMvc.perform(patch("/api/Meeting/m1")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật cuộc họp thành công."));
    }

    @Test
    void updateMeetingReturns409OnConflict() throws Exception {
        UpdateMeetingRequest req = new UpdateMeetingRequest(
                "m1", "Tên mới", "Mô tả mới", "Agenda mới",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                "Asia/Bangkok", 1, null, "stale-version", null, null
        );
        doThrow(new IllegalStateException("Dữ liệu đã được thay đổi bởi phiên làm việc khác."))
                .when(meetingService).updateMeeting(eq("user1"), eq("m1"), any());

        mockMvc.perform(patch("/api/Meeting/m1")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu đã được thay đổi bởi phiên làm việc khác."));
    }

    @Test
    void deleteMeetingReturns200() throws Exception {
        mockMvc.perform(delete("/api/Meeting/m1").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Xóa cuộc họp thành công."));

        verify(meetingService).deleteMeeting("user1", "m1");
    }

    @Test
    void cancelMeetingReturns200() throws Exception {
        CancelMeetingRequest req = new CancelMeetingRequest("m1", "Lý do hủy");

        mockMvc.perform(post("/api/Meeting/m1/cancel")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Hủy cuộc họp thành công."));

        verify(meetingService).cancelMeeting(eq("user1"), eq("m1"), any());
    }

    @Test
    void archiveMeetingReturns200() throws Exception {
        mockMvc.perform(post("/api/Meeting/m1/archive").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Lưu trữ cuộc họp thành công."));

        verify(meetingService).archiveMeeting("user1", "m1");
    }

    @Test
    void createQuickMeetingReturns200() throws Exception {
        QuickMeetingRequest req = new QuickMeetingRequest("Họp nhanh", List.of());
        MeetingDetailResponse detail = sampleDetail("m1");
        when(meetingService.createQuickMeeting(eq("user1"), any())).thenReturn(detail);

        mockMvc.perform(post("/api/Meeting/quick")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Tạo cuộc họp nhanh thành công."));
    }

    @Test
    void addParticipantsReturns200() throws Exception {
        UpdateMeetingParticipantsRequest req = new UpdateMeetingParticipantsRequest(List.of(), List.of("user2"));
        MeetingDetailResponse detail = sampleDetail("m1");
        when(meetingService.addParticipants(eq("user1"), eq("m1"), any())).thenReturn(detail);

        mockMvc.perform(post("/api/Meeting/m1/participants")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Thêm thành viên thành công."));
    }

    @Test
    void removeParticipantReturns200() throws Exception {
        mockMvc.perform(delete("/api/Meeting/m1/participants/user2").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Xóa thành viên thành công."));

        verify(meetingService).removeParticipant("user1", "m1", "user2");
    }

    @Test
    void getJoinInfoReturns200() throws Exception {
        MeetingJoinInfoResponse joinInfo = new MeetingJoinInfoResponse(
                "m1", "Họp A", "room123", "/meeting/room123", 1,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                "meet.d2s.vn", "room123", "User 1", true, false
        );
        when(meetingService.getJoinInfo("user1", "m1")).thenReturn(joinInfo);

        mockMvc.perform(get("/api/Meeting/m1/join-info").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.roomName").value("room123"))
                .andExpect(jsonPath("$.data.domain").value("meet.d2s.vn"))
                .andExpect(jsonPath("$.data.externalApiUrl").value("https://meet.d2s.vn/external_api.js"));
    }

    @Test
    void startMeetingReturns200() throws Exception {
        mockMvc.perform(post("/api/Meeting/m1/start").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Bắt đầu cuộc họp thành công."));

        verify(meetingService).startMeeting("user1", "m1");
    }

    @Test
    void endMeetingReturns200() throws Exception {
        mockMvc.perform(post("/api/Meeting/m1/end").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Kết thúc cuộc họp thành công."));

        verify(meetingService).endMeeting("user1", "m1");
    }

    @Test
    void joinMeetingReturns200() throws Exception {
        mockMvc.perform(post("/api/Meeting/m1/join").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Tham gia cuộc họp thành công."));

        verify(meetingService).joinMeeting("user1", "m1");
    }

    @Test
    void leaveMeetingReturns200() throws Exception {
        mockMvc.perform(post("/api/Meeting/m1/leave").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Rời cuộc họp thành công."));

        verify(meetingService).leaveMeeting("user1", "m1");
    }

    @Test
    void getMessagesReturns200() throws Exception {
        MeetingMessageResponse msg = new MeetingMessageResponse("msg1", "m1", "user1", "User 1", "", "Hello", LocalDateTime.now());
        when(meetingService.getMessages("user1", "m1")).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/Meeting/m1/messages").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].messageText").value("Hello"));
    }

    @Test
    void sendMessageReturns200() throws Exception {
        SendMeetingMessageRequest req = new SendMeetingMessageRequest("", "Hello");
        MeetingMessageResponse msg = new MeetingMessageResponse("msg1", "m1", "user1", "User 1", "", "Hello", LocalDateTime.now());
        when(meetingService.sendMessage(eq("user1"), eq("m1"), any())).thenReturn(msg);

        mockMvc.perform(post("/api/Meeting/m1/messages")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.messageText").value("Hello"))
                .andExpect(jsonPath("$.message").value("Gửi tin nhắn thành công."));
    }

    @Test
    void getAiRagStatusReturns200() throws Exception {
        vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse statusResp = new vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse(
                "m1", true, 5, LocalDateTime.now(), "5 chunks"
        );
        when(ragService.getRagStatus("m1")).thenReturn(statusResp);

        mockMvc.perform(get("/api/Meeting/m1/ai/status").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.indexed").value(true))
                .andExpect(jsonPath("$.data.chunkCount").value(5));
    }

    @Test
    void syncAiRagReturns200() throws Exception {
        vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse statusResp = new vn.com.d2s.smr.dto.mt.ai.MeetingRagStatusResponse(
                "m1", true, 8, LocalDateTime.now(), "8 chunks"
        );
        when(ragService.syncKnowledgeBase("m1", "user1")).thenReturn(statusResp);

        mockMvc.perform(post("/api/Meeting/m1/ai/sync").principal(() -> "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.chunkCount").value(8))
                .andExpect(jsonPath("$.message").value("Đồng bộ cơ sở tri thức cuộc họp thành công."));
    }

    @Test
    void askMeetingAiReturns200() throws Exception {
        vn.com.d2s.smr.dto.mt.ai.MeetingAiChatRequest req = new vn.com.d2s.smr.dto.mt.ai.MeetingAiChatRequest("Tóm tắt cuộc họp", null);
        vn.com.d2s.smr.dto.mt.ai.MeetingAiChatResponse resp = vn.com.d2s.smr.dto.mt.ai.MeetingAiChatResponse.success("Đây là tóm tắt", List.of(), "default");
        when(ragService.askMeetingAssistant("m1", "user1", "Tóm tắt cuộc họp")).thenReturn(resp);

        mockMvc.perform(post("/api/Meeting/m1/ai/chat")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.answer").value("Đây là tóm tắt"))
                .andExpect(jsonPath("$.data.model").value("default"));
    }

    private static MeetingListItemResponse sampleItem(String id) {
        return new MeetingListItemResponse(
                id, "Họp A", "Mô tả", LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                1, 1, "room123", "/meeting/room123", 2, true, "User 1"
        );
    }

    private static MeetingDetailResponse sampleDetail(String id) {
        return new MeetingDetailResponse(
                id, "Họp A", "Mô tả", LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                1, 1, "room123", "/meeting/room123", 2, true, "User 1",
                "Agenda", "Asia/Bangkok", "", null, List.of(), List.of(), "0x0000000000000001", true
        );
    }
}
