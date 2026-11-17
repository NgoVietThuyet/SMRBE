package vn.com.d2s.smr.service.mt.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.dto.mt.minutes.MinutesContent;
import vn.com.d2s.smr.entity.mt.MeetingTranscriptSegment;
import vn.com.d2s.smr.service.mt.minutes.MinutesAiGenerator;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.CleanTurn;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.DocDigest;
import vn.com.d2s.smr.service.mt.pipeline.PipelineModels.Glossary;
import vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.FakeLlm;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.T0;
import static vn.com.d2s.smr.service.mt.pipeline.PipelineTestSupport.seg;

class TranscriptCleanerAndExtractorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Glossary glossary = new Glossary(List.of("Nguyễn Minh Đức"), List.of("Coreach"));

    private List<MeetingTranscriptSegment> segs() {
        return List.of(
                seg("s1", "duc", "Minh Đức", "ừm thì là dự án co rích", 1),
                seg("s2", "duc", "Minh Đức", "à tiến độ được tám mươi phần trăm", 2),
                seg("s3", "tu", "Đức Tú", "ok", 3));
    }

    @Test
    void cleanerMergesSameSpeakerFixesTermsAndKeepsSourceIds() {
        FakeLlm llm = new FakeLlm((sys, user) -> "{\"turns\":[{\"ids\":[\"S1\",\"S2\"],\"text\":\"Dự án Coreach tiến độ được 80%.\"},{\"ids\":[\"S3\"],\"text\":\"Ok.\"}]}");

        List<CleanTurn> out = TranscriptCleaner.clean(segs(), glossary, llm, mapper);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).segmentIds()).containsExactly("s1", "s2");
        assertThat(out.get(0).text()).isEqualTo("Dự án Coreach tiến độ được 80%.");
        assertThat(out.get(0).raw()).contains("co rích").contains("tám mươi");
        assertThat(out.get(0).speaker()).isEqualTo("Minh Đức");
        assertThat(llm.users.get(0)).contains("Coreach").contains("[S1] Minh Đức").contains("DANH SÁCH TỪ KHÓA GỐC");
    }

    @Test
    void cleanerNeverLosesSpeechWhenAiSkipsMergesSpeakersOrReturnsGarbage() {
        // AI bỏ sót S2 và gộp lời của hai người (S1 + S3) → S1,S2,S3 đều rơi về nguyên văn.
        FakeLlm skip = new FakeLlm((s, u) -> "{\"turns\":[{\"ids\":[\"S1\",\"S3\"],\"text\":\"gộp sai\"}]}");
        List<CleanTurn> a = TranscriptCleaner.clean(segs(), glossary, skip, mapper);
        assertThat(a).hasSize(3);
        assertThat(a.get(0).text()).contains("co rích");
        assertThat(a.get(1).segmentIds()).containsExactly("s2");

        FakeLlm garbage = new FakeLlm((s, u) -> "không phải json");
        assertThat(TranscriptCleaner.clean(segs(), glossary, garbage, mapper)).hasSize(3);

        FakeLlm down = new FakeLlm((s, u) -> null);
        assertThat(TranscriptCleaner.clean(segs(), glossary, down, mapper)).extracting(CleanTurn::text)
                .containsExactly("ừm thì là dự án co rích", "à tiến độ được tám mươi phần trăm", "ok");
    }

    private static final String RESULT = "{\"dien_bien_thao_luan\":[{\"chu_de\":\"Cách triển khai\",\"dien_bien\":\"Đức đề xuất cách 1, Tú phản đối và chốt cách 2.\",\"sources\":[\"T1\",\"T2\"]}],"
            + "\"tom_tat\":\"Cuộc họp bàn cách triển khai.\",\"tom_tat_sources\":[\"T1\"],"
            + "\"quyet_dinh_cuoi_cung\":[{\"text\":\"Triển khai theo cách 2.\",\"sources\":[\"T2\",\"T99\"]}],"
            + "\"action_items\":[{\"text\":\"Chuẩn bị tài liệu\",\"assignee\":\"duc\",\"due_date\":\"2026-10-20\",\"sources\":[\"T1\"]},"
            + "{\"text\":\"Việc không rõ\",\"assignee\":\"ai-do\",\"due_date\":\"mai\",\"sources\":[]}]}";

    private List<CleanTurn> turns() {
        return List.of(
                new CleanTurn(List.of("s1", "s2"), "Minh Đức", "Dự án Coreach đề xuất cách 1.", "", T0.plusMinutes(1)),
                new CleanTurn(List.of("s3"), "Đức Tú", "Không, làm cách 2.", "", T0.plusMinutes(3)));
    }

    private final List<MinutesAiGenerator.Participant> people = List.of(
            new MinutesAiGenerator.Participant("duc", "Minh Đức"), new MinutesAiGenerator.Participant("tu", "Đức Tú"));

    @Test
    void extractorWritesDiscussionBeforeDecisionsAndMapsSources() {
        FakeLlm llm = new FakeLlm((s, u) -> RESULT);

        MinutesContent c = MeetingExtractor.extract("Họp Coreach", T0, people, turns(), List.of(new DocDigest("kh.docx", "- mục tiêu 80%")),
                llm, mapper, 40_000);

        assertThat(llm.users).hasSize(1);
        // Prompt ép thứ tự diễn biến → quyết định cuối cùng, kèm tóm tắt tài liệu và transcript đã làm sạch có mã T.
        String sys = llm.systems.get(0);
        assertThat(sys.indexOf("dien_bien_thao_luan")).isLessThan(sys.indexOf("quyet_dinh_cuoi_cung"));
        assertThat(llm.users.get(0)).contains("[T1 01:00] Minh Đức:").contains("[T2 03:00] Đức Tú:").contains("mục tiêu 80%");

        assertThat(c.discussion()).hasSize(1);
        assertThat(c.discussion().get(0).sources()).containsExactly("s1", "s2", "s3");
        assertThat(c.summary()).isEqualTo("Cuộc họp bàn cách triển khai.");
        assertThat(c.decisions().get(0).text()).isEqualTo("Triển khai theo cách 2.");
        assertThat(c.decisions().get(0).sources()).containsExactly("s3");        // T99 không tồn tại → bỏ
        assertThat(c.actions()).hasSize(2);
        assertThat(c.actions().get(0).assignee()).isEqualTo("duc");
        assertThat(c.actions().get(0).dueDate()).isEqualTo("2026-10-20");
        assertThat(c.actions().get(1).assignee()).isNull();
        assertThat(c.actions().get(1).dueDate()).isNull();
        assertThat(c.actions().get(1).sources()).isEmpty();
    }

    @Test
    void longMeetingIsSplitIntoWindowsThenMerged() {
        List<CleanTurn> many = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            many.add(new CleanTurn(List.of("g" + i), "Minh Đức", "x".repeat(600), "", T0.plusMinutes(i)));
        }
        FakeLlm llm = new FakeLlm((s, u) -> RESULT);

        MinutesContent c = MeetingExtractor.extract("Họp dài", T0, people, many, List.of(), llm, mapper, 5_000);

        // 80 lượt × ~650 ký tự ≈ 52k → 3 phần (cửa sổ 20k) + 1 lần hợp nhất.
        assertThat(llm.users.size()).isEqualTo(4);
        assertThat(llm.systems.get(3)).contains("hợp nhất");
        assertThat(llm.users.get(1)).contains("KẾT QUẢ CÁC PHẦN TRƯỚC");
        assertThat(c.decisions()).hasSize(1);
    }

    @Test
    void extractorFailsClearlyWhenAiIsDownOrReturnsNothingUseful() {
        assertThatThrownBy(() -> MeetingExtractor.extract("M", T0, people, turns(), List.of(), new FakeLlm((s, u) -> null), mapper, 40_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("máy chủ AI");
        assertThatThrownBy(() -> MeetingExtractor.extract("M", T0, people, turns(), List.of(),
                new FakeLlm((s, u) -> "{\"dien_bien_thao_luan\":[],\"tom_tat\":\"\"}"), mapper, 40_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("rỗng");
        assertThatThrownBy(() -> MeetingExtractor.extract("M", T0, people, List.of(), List.of(), new FakeLlm((s, u) -> RESULT), mapper, 40_000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("chưa có transcript");
    }
}
