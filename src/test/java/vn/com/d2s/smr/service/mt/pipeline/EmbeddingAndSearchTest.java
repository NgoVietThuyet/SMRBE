package vn.com.d2s.smr.service.mt.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.service.mt.pipeline.HybridSearch.Candidate;
import vn.com.d2s.smr.service.mt.pipeline.embedding.EmbeddingException;
import vn.com.d2s.smr.service.mt.pipeline.embedding.VectorCodec;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingAndSearchTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private List<float[]> parse(String body, int expected) throws Exception {
        Method m = Class.forName("vn.com.d2s.smr.service.mt.pipeline.embedding.HttpEmbeddingClient")
                .getDeclaredMethod("parse", String.class, int.class, ObjectMapper.class);
        m.setAccessible(true);
        try {
            return (List<float[]>) m.invoke(null, body, expected, mapper);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void parsesNestedArrayOpenAiShapeAndSortsByIndex() throws Exception {
        List<float[]> a = parse("[{\"index\":1,\"embedding\":[[0,3]]},{\"index\":0,\"embedding\":[[3,0]]}]", 2);
        assertThat(a.get(0)).containsExactly(1f, 0f);   // đã chuẩn hóa độ dài 1, theo index
        assertThat(a.get(1)).containsExactly(0f, 1f);
        List<float[]> b = parse("{\"data\":[{\"index\":0,\"embedding\":[2,0]}]}", 1);
        assertThat(b.get(0)).containsExactly(1f, 0f);
    }

    @Test
    void rejectsEmptyWrongCountOrBadShape() {
        assertThatThrownBy(() -> parse("", 1)).isInstanceOf(EmbeddingException.class);
        assertThatThrownBy(() -> parse("[{\"index\":0,\"embedding\":[[1]]}]", 2)).isInstanceOf(EmbeddingException.class);
        assertThatThrownBy(() -> parse("{\"x\":1}", 1)).isInstanceOf(EmbeddingException.class);
    }

    @Test
    void vectorCodecRoundTripsAndComputesCosine() {
        float[] v = VectorCodec.normalize(new float[]{3f, 4f});
        assertThat(VectorCodec.decode(VectorCodec.encode(v))).containsExactly(v);
        assertThat(VectorCodec.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(VectorCodec.cosine(v, new float[]{1f})).isEqualTo(-1);
        assertThat(VectorCodec.decode(new byte[]{1, 2, 3})).isNull();
    }

    @Test
    void bm25FindsExactProperNameEvenWithoutVectors() {
        List<Candidate> c = List.of(
                new Candidate("a", "Chúng ta bàn về kế hoạch tuyển dụng quý sau", null),
                new Candidate("b", "Anh Nguyễn Minh Đức báo cáo tiến độ dự án Coreach", null),
                new Candidate("c", "Thảo luận về ngân sách marketing", null));

        Map<String, Double> s = HybridSearch.score(c, "Minh Đức báo cáo gì về Coreach", null);

        assertThat(s.get("b")).isGreaterThan(s.get("a")).isGreaterThan(0.0);
        assertThat(s.get("c")).isZero();
    }

    @Test
    void vectorSimilarityCanRescueChunkWithoutSharedWords() {
        float[] q = VectorCodec.normalize(new float[]{1f, 0.1f});
        List<Candidate> c = List.of(
                new Candidate("near", "kết luận về chi phí", VectorCodec.normalize(new float[]{1f, 0.2f})),
                new Candidate("far", "chuyện khác hoàn toàn", VectorCodec.normalize(new float[]{-0.1f, 1f})));

        Map<String, Double> s = HybridSearch.score(c, "doanh thu năm nay", q);

        assertThat(s.get("near")).isGreaterThan(0.0);
        assertThat(s.get("far")).isZero();
    }
}
