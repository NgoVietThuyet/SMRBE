package vn.com.d2s.smr.service.mt.pipeline;

import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.CleanTurn;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.Glossary;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.T0;

class GlossaryAndChunkerTest {

    @Test
    void glossaryTakesNamesAcronymsProjectPhrasesAndNumbers() {
        MinutesAiGenerator.Doc doc = new MinutesAiGenerator.Doc("ke-hoach.docx",
                "Dự án Smart Meeting do phòng PTPM phụ trách. Mục tiêu doanh thu 12,5 tỷ đồng trong 6 tháng. Smart Meeting giai đoạn 2.");

        Glossary g = GlossaryExtractor.extract("Họp triển khai Smart Meeting", "Chương trình: KPI quý", List.of("Nguyễn Minh Đức", "Lý Đức Tú"), List.of(doc));

        assertThat(g.names()).containsExactly("Nguyễn Minh Đức", "Lý Đức Tú");
        assertThat(g.terms()).contains("PTPM", "KPI", "Smart Meeting");
        assertThat(g.terms()).anyMatch(t -> t.startsWith("12,5 tỷ"));
        assertThat(g.toPromptText()).contains("Tên người tham dự").contains("Smart Meeting");
    }

    private static List<CleanTurn> turns(int n) {
        List<CleanTurn> t = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            t.add(new CleanTurn(List.of("seg" + i), i % 2 == 0 ? "An" : "Bình", "câu số " + i, "câu số " + i, T0.plusMinutes(i)));
        }
        return t;
    }

    @Test
    void transcriptWindowsOverlapByTwoTurnsAndCarryMetadata() {
        List<RagChunker.Spec> chunks = RagChunker.transcriptChunks(turns(10), T0);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).contains("câu số 0").contains("câu số 5").doesNotContain("câu số 6");
        assertThat(chunks.get(1).content()).contains("câu số 4").contains("câu số 5").contains("câu số 9");
        assertThat(chunks.get(0).kind()).isEqualTo("TRANSCRIPT");
        assertThat(chunks.get(0).speaker()).contains("An").contains("Bình");
        assertThat(chunks.get(0).offset()).isEqualTo("00:00");
        assertThat(chunks.get(1).offset()).isEqualTo("04:00");
        assertThat(chunks.get(0).refs()).contains("seg0").contains("seg5");
    }

    @Test
    void shortTranscriptIsOneChunk() {
        assertThat(RagChunker.transcriptChunks(turns(3), T0)).hasSize(1);
        assertThat(RagChunker.transcriptChunks(List.of(), T0)).isEmpty();
    }

    @Test
    void docsSplitByPageOrByParagraphs() {
        List<RagChunker.Spec> paged = RagChunker.docChunks(new MinutesAiGenerator.Doc("slide.pdf", "Trang một nói về A\fTrang hai nói về B\f"));
        assertThat(paged).hasSize(2);
        assertThat(paged.get(1).title()).contains("trang 2");
        assertThat(paged.get(1).refs()).isEqualTo("slide.pdf#2");
        assertThat(paged.get(0).kind()).isEqualTo("DOC");

        String longText = ("Đoạn văn dài. ").repeat(400);
        List<RagChunker.Spec> unpaged = RagChunker.docChunks(new MinutesAiGenerator.Doc("bao-cao.docx", longText));
        assertThat(unpaged).hasSizeGreaterThan(2);
        assertThat(unpaged).allMatch(c -> c.content().length() < RagChunker.DOC_CHUNK_CHARS + 200);
    }
}
