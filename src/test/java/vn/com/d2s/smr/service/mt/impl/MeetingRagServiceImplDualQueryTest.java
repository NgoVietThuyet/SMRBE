package vn.com.d2s.smr.service.mt.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import vn.com.d2s.smr.config.LlmProperties;
import vn.com.d2s.smr.dto.mt.ai.MeetingAiChatResponse;
import vn.com.d2s.smr.entity.mt.MeetingInfo;
import vn.com.d2s.smr.entity.mt.MtMinutes;
import vn.com.d2s.smr.entity.mt.MtRagChunk;
import vn.com.d2s.smr.repository.mt.MeetingInfoRepository;
import vn.com.d2s.smr.repository.mt.MeetingPersonalRepository;
import vn.com.d2s.smr.repository.mt.MeetingTaskRepository;
import vn.com.d2s.smr.repository.mt.MtMinutesRepository;
import vn.com.d2s.smr.service.cf.FileService;
import vn.com.d2s.smr.service.mt.LlmClientService;
import vn.com.d2s.smr.service.mt.MeetingTranscriptService;
import vn.com.d2s.smr.service.mt.pipeline.MeetingIndexStore;
import vn.com.d2s.smr.service.mt.pipeline.embedding.EmbeddingClient;
import vn.com.d2s.smr.service.mt.pipeline.embedding.VectorCodec;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Bước 4: câu trả lời lấy cùng lúc biên bản đã chốt + đoạn tài liệu + đoạn transcript liên quan (tìm kiếm kết hợp). */
class MeetingRagServiceImplDualQueryTest {

    private static MtRagChunk chunk(String id, String kind, String title, String content, float[] vec, int ord) {
        MtRagChunk c = new MtRagChunk();
        c.setId(id);
        c.setMeetingId("m1");
        c.setKind(kind);
        c.setTitle(title);
        c.setContent(content);
        c.setRefs("seg" + ord);
        c.setOffsetText("00:0" + ord);
        c.setEmbedding(vec == null ? null : VectorCodec.encode(VectorCodec.normalize(vec)));
        c.setOrd(ord);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    @Test
    void answerContextPinsApprovedMinutesAndRetrievesDocAndTranscriptChunks() {
        MeetingInfoRepository meetings = Mockito.mock(MeetingInfoRepository.class);
        MeetingPersonalRepository people = Mockito.mock(MeetingPersonalRepository.class);
        MeetingTaskRepository tasks = Mockito.mock(MeetingTaskRepository.class);
        MeetingTranscriptService transcripts = Mockito.mock(MeetingTranscriptService.class);
        FileService files = Mockito.mock(FileService.class);
        LlmClientService llm = Mockito.mock(LlmClientService.class);
        MtMinutesRepository minutesRepo = Mockito.mock(MtMinutesRepository.class);
        MeetingIndexStore index = Mockito.mock(MeetingIndexStore.class);

        MeetingInfo m = new MeetingInfo();
        m.setId("m1");
        m.setName("Họp kế hoạch");
        m.setStatus(3);
        when(meetings.findById("m1")).thenReturn(Optional.of(m));
        when(people.findByMeetingId("m1")).thenReturn(List.of());
        when(tasks.findByMeetingId("m1")).thenReturn(List.of());
        when(transcripts.getTranscriptsByMeetingId("m1")).thenReturn(List.of());

        MtMinutes approved = new MtMinutes();
        approved.setId("min1");
        approved.setVersion(2);
        approved.setStatus(MtMinutes.APPROVED);
        approved.setApprovedAt(LocalDateTime.now());
        approved.setContentJson("{\"summary\":\"Họp chốt doanh thu.\",\"decisions\":[{\"id\":\"d\",\"text\":\"Chốt doanh thu 12 tỷ\",\"sources\":[]}],"
                + "\"discussion\":[{\"topic\":\"Doanh thu\",\"text\":\"An đề xuất 10 tỷ, Bình đề xuất 12 tỷ và được chốt.\",\"sources\":[]}]}");
        when(minutesRepo.findByMeetingIdOrderByVersionDesc("m1")).thenReturn(List.of(approved));

        when(index.chunks("m1")).thenReturn(List.of(
                chunk("c1", MtRagChunk.TRANSCRIPT, "Transcript 00:01", "[⏱ 00:01] An: nên đặt chỉ tiêu doanh thu 10 tỷ", new float[]{1f, 0f, 0f}, 1),
                chunk("c2", MtRagChunk.DOC, "Tài liệu: ke-hoach.pdf (trang 2)", "Chỉ tiêu doanh thu kế hoạch năm: 9 tỷ đồng", new float[]{0.9f, 0.1f, 0f}, 2),
                chunk("c3", MtRagChunk.TRANSCRIPT, "Transcript 00:09", "[⏱ 00:09] Bình: chuyện nhân sự tháng sau", new float[]{0f, 0f, 1f}, 3)));
        when(llm.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("Chỉ tiêu cuối cùng là 12 tỷ.");

        EmbeddingClient embeddings = new EmbeddingClient() {
            public boolean available() {
                return true;
            }

            public List<float[]> embed(List<String> t) {
                List<float[]> out = new ArrayList<>();
                t.forEach(x -> out.add(VectorCodec.normalize(new float[]{1f, 0f, 0f})));
                return out;
            }
        };

        MeetingRagServiceImpl service = new MeetingRagServiceImpl(meetings, people, tasks, transcripts, files, llm,
                new LlmProperties("http://x", "k", "default", true, null, 1000, 1000));
        service.setMinutesRepository(minutesRepo);
        service.setIndexStore(index);
        service.setEmbeddingClient(embeddings);

        MeetingAiChatResponse r = service.askMeetingAssistant("m1", "u1", "So sánh chỉ tiêu doanh thu trong tài liệu kế hoạch với con số đã chốt");

        assertThat(r.answer()).contains("12 tỷ");
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llm).generate(system.capture(), eq("So sánh chỉ tiêu doanh thu trong tài liệu kế hoạch với con số đã chốt"), anyDouble(), anyInt());
        String ctx = system.getValue();
        // 1) Biên bản đã chốt (kể cả mới ở trạng thái đã duyệt) nằm trước, có diễn biến + quyết định cuối cùng.
        assertThat(ctx).contains("Biên bản đã chốt").contains("Chốt doanh thu 12 tỷ").contains("Diễn biến thảo luận");
        assertThat(ctx.indexOf("Biên bản đã chốt")).isLessThan(ctx.indexOf("Tài liệu: ke-hoach.pdf"));
        // 2) Đoạn tài liệu và 3) đoạn transcript liên quan được lấy; đoạn không liên quan (nhân sự) thì không.
        assertThat(ctx).contains("Tài liệu: ke-hoach.pdf (trang 2)").contains("nên đặt chỉ tiêu doanh thu 10 tỷ");
        assertThat(ctx).doesNotContain("chuyện nhân sự tháng sau");
    }
}
