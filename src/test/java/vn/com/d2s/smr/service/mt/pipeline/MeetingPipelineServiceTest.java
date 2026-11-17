package vn.com.d2s.smr.service.mt.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.entity.mt.MtMeetingAnalysis;
import vn.com.d2s.smr.entity.mt.MtRagChunk;
import vn.com.d2s.smr.repository.mt.MtMeetingAnalysisRepository;
import vn.com.d2s.smr.repository.mt.MtRagChunkRepository;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.FakeLlm;
import vn.com.d2s.smr.service.mt.pipeline.embedding.EmbeddingClient;
import vn.com.d2s.smr.service.mt.pipeline.embedding.EmbeddingException;
import vn.com.d2s.smr.service.mt.pipeline.embedding.VectorCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.T0;
import static vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.seg;

class MeetingPipelineServiceTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MtMeetingAnalysisRepository analyses = Mockito.mock(MtMeetingAnalysisRepository.class);
    private final MtRagChunkRepository chunkRepo = Mockito.mock(MtRagChunkRepository.class);
    private final MeetingIndexStore store = new MeetingIndexStore(analyses, chunkRepo);

    private static final String CLEAN = "{\"turns\":[{\"ids\":[\"S1\"],\"text\":\"Triển khai cách 2.\"},{\"ids\":[\"S2\"],\"text\":\"Đồng ý.\"}]}";
    private static final String RESULT = "{\"dien_bien_thao_luan\":[{\"chu_de\":\"A\",\"dien_bien\":\"Chốt cách 2.\",\"sources\":[\"T1\"]}],"
            + "\"tom_tat\":\"Tóm tắt.\",\"tom_tat_sources\":[\"T1\"],\"quyet_dinh_cuoi_cung\":[{\"text\":\"Cách 2\",\"sources\":[\"T1\"]}],\"action_items\":[]}";

    private FakeLlm llm() {
        return new FakeLlm((sys, user) -> sys.contains("biên tập viên") ? CLEAN : sys.contains("Bạn tóm tắt tài liệu") ? "- ý chính" : RESULT);
    }

    private MeetingPipelineService.Input input() {
        return new MeetingPipelineService.Input("m1", "Họp", T0, "Chương trình", List.of(new MinutesAiGenerator.Participant("duc", "Minh Đức")),
                List.of(seg("s1", "duc", "Minh Đức", "ừm làm cách hai", 1), seg("s2", "tu", "Đức Tú", "ok", 2)),
                List.of(new MinutesAiGenerator.Doc("kh.pdf", "Trang 1 về KPI\fTrang 2 về ngân sách")), "hash1");
    }

    private EmbeddingClient embeddings(boolean fail) {
        return new EmbeddingClient() {
            public boolean available() {
                return true;
            }

            public List<float[]> embed(List<String> texts) {
                if (fail) throw new EmbeddingException("down");
                List<float[]> out = new ArrayList<>();
                texts.forEach(t -> out.add(VectorCodec.normalize(new float[]{1f, t.length()})));
                return out;
            }
        };
    }

    @Test
    void runsAllStepsAndStoresAnalysisAndEmbeddedChunks() {
        when(analyses.findByMeetingId("m1")).thenReturn(Optional.empty());
        FakeLlm llm = llm();

        MinutesContent c = new MeetingPipelineService(llm, mapper, store, embeddings(false), 40_000).run(input());

        assertThat(c.decisions()).hasSize(1);
        assertThat(c.decisions().get(0).sources()).containsExactly("s1");
        assertThat(c.discussion()).hasSize(1);
        // Thứ tự gọi AI: tóm tắt tài liệu → làm sạch → trích xuất.
        assertThat(llm.systems).hasSize(3);
        assertThat(llm.systems.get(2)).contains("DIỄN BIẾN TRƯỚC, CHỐT SAU");
        assertThat(llm.users.get(2)).contains("- ý chính").contains("Triển khai cách 2.");   // dùng bản sạch + tóm tắt tài liệu

        ArgumentCaptor<MtMeetingAnalysis> a = ArgumentCaptor.forClass(MtMeetingAnalysis.class);
        verify(analyses).save(a.capture());
        assertThat(a.getValue().getMeetingId()).isEqualTo("m1");
        assertThat(a.getValue().getSourceHash()).isEqualTo("hash1");
        assertThat(a.getValue().getCleanedJson()).contains("Triển khai cách 2.").contains("s1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MtRagChunk>> chunks = ArgumentCaptor.forClass(Iterable.class);
        verify(chunkRepo).deleteByMeetingId("m1");
        verify(chunkRepo).saveAll(chunks.capture());
        List<MtRagChunk> saved = new ArrayList<>();
        chunks.getValue().forEach(saved::add);
        assertThat(saved).extracting(MtRagChunk::getKind).containsExactly("TRANSCRIPT", "DOC", "DOC");
        assertThat(saved).allMatch(x -> x.getEmbedding() != null && x.getMeetingId().equals("m1"));
        assertThat(saved.get(2).getRefs()).isEqualTo("kh.pdf#2");
    }

    @Test
    void embeddingOutageStillStoresKeywordOnlyChunksAndMinutes() {
        when(analyses.findByMeetingId("m1")).thenReturn(Optional.empty());

        MinutesContent c = new MeetingPipelineService(llm(), mapper, store, embeddings(true), 40_000).run(input());

        assertThat(c.decisions()).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MtRagChunk>> chunks = ArgumentCaptor.forClass(Iterable.class);
        verify(chunkRepo).saveAll(chunks.capture());
        chunks.getValue().forEach(x -> assertThat(x.getEmbedding()).isNull());
    }

    @Test
    void storageFailureDoesNotLoseTheMinutes() {
        when(analyses.findByMeetingId(any())).thenThrow(new RuntimeException("db down"));

        MinutesContent c = new MeetingPipelineService(llm(), mapper, store, embeddings(false), 40_000).run(input());

        assertThat(c.decisions()).hasSize(1);
    }

    @Test
    void extractionFailureIsReportedSoCallerCanFallBack() {
        FakeLlm bad = new FakeLlm((sys, user) -> sys.contains("biên tập viên") ? CLEAN : sys.contains("Bạn tóm tắt tài liệu") ? "x" : "không phải json");

        assertThatThrownBy(() -> new MeetingPipelineService(bad, mapper, store, embeddings(false), 40_000).run(input()))
                .isInstanceOf(IllegalStateException.class);
    }
}
