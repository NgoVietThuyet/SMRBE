package vn.com.d2s.smr.service.mt.pipeline;

import vn.com.d2s.smr.common.TextSearch;
import vn.com.d2s.smr.service.mt.pipeline.embedding.VectorCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bước 4: tìm kiếm kết hợp. BM25 (từ khóa chính xác, không phân biệt dấu — không sót tên riêng) + cosine trên vector embedding,
 * hợp nhất bằng Reciprocal Rank Fusion, rồi xếp hạng lại theo độ phủ từ khóa của câu hỏi. Không có vector thì chỉ dùng BM25.
 */
public final class HybridSearch {

    static final double K1 = 1.5;
    static final double B = 0.75;
    static final int RRF_K = 60;

    private HybridSearch() {
    }

    public record Candidate(String id, String text, float[] vector) {
    }

    /** @return điểm 0..1 cho từng id (cao hơn = sát câu hỏi hơn). Đoạn không liên quan có điểm 0. */
    public static Map<String, Double> score(List<Candidate> candidates, String query, float[] queryVector) {
        Map<String, Double> out = new HashMap<>();
        if (candidates.isEmpty()) return out;
        List<String> qTokens = tokens(query).stream().distinct().toList();

        // --- BM25 ---
        List<List<String>> docs = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        double avgLen = 0;
        for (Candidate c : candidates) {
            List<String> t = tokens(c.text());
            docs.add(t);
            avgLen += t.size();
            for (String w : new HashSet<>(t)) df.merge(w, 1, Integer::sum);
        }
        avgLen = Math.max(1, avgLen / candidates.size());
        double[] bm25 = new double[candidates.size()];
        int n = candidates.size();
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = new HashMap<>();
            docs.get(i).forEach(w -> tf.merge(w, 1, Integer::sum));
            for (String q : qTokens) {
                int f = tf.getOrDefault(q, 0);
                if (f == 0) continue;
                double idf = Math.log(1 + (n - df.getOrDefault(q, 0) + 0.5) / (df.getOrDefault(q, 0) + 0.5));
                bm25[i] += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * docs.get(i).size() / avgLen));
            }
        }

        // --- Vector ---
        double[] vec = new double[n];
        boolean hasVec = queryVector != null;
        for (int i = 0; i < n; i++) {
            vec[i] = hasVec && candidates.get(i).vector() != null ? VectorCodec.cosine(queryVector, candidates.get(i).vector()) : Double.NEGATIVE_INFINITY;
        }

        // --- RRF ---
        double[] rrf = new double[n];
        rankInto(bm25, rrf, 0.0);           // chỉ xếp hạng các đoạn có điểm BM25 > 0
        if (hasVec) rankInto(vec, rrf, 0.05); // chỉ xếp hạng các đoạn có cosine > 0.05
        double maxRrf = 0;
        for (double v : rrf) maxRrf = Math.max(maxRrf, v);

        // --- Rerank: độ phủ từ khóa của câu hỏi trong đoạn ---
        for (int i = 0; i < n; i++) {
            if (rrf[i] <= 0) {
                out.put(candidates.get(i).id(), 0.0);
                continue;
            }
            Set<String> have = new HashSet<>(docs.get(i));
            long hit = qTokens.stream().filter(have::contains).count();
            double coverage = qTokens.isEmpty() ? 0 : (double) hit / qTokens.size();
            out.put(candidates.get(i).id(), 0.6 * (rrf[i] / maxRrf) + 0.4 * coverage);
        }
        return out;
    }

    private static void rankInto(double[] scores, double[] rrf, double minScore) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > minScore) idx.add(i);
        }
        idx.sort(Comparator.comparingDouble((Integer i) -> scores[i]).reversed());
        for (int r = 0; r < idx.size(); r++) rrf[idx.get(r)] += 1.0 / (RRF_K + r + 1);
    }

    /** Từ phổ thông tiếng Việt (đã bỏ dấu) không mang nội dung tìm kiếm: bỏ ra để "về", "của", "là"... không khớp bừa. */
    private static final Set<String> STOP = Set.of("va", "cua", "la", "co", "cac", "cho", "mot", "nay", "duoc", "trong", "nhung", "khi",
            "voi", "gi", "nao", "khong", "thi", "ma", "da", "se", "de", "ve", "ra", "vao", "tu", "den", "nhu", "cung", "hay", "hoac",
            "rat", "nhieu", "bao", "sao", "the", "ai", "dau", "o", "tai", "theo", "sau", "truoc", "do", "kia", "day", "con", "nen");

    /** Tách từ không dấu, chữ thường; bỏ từ 1 ký tự và từ phổ thông. */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        for (String t : TextSearch.fold(s).split("[^a-z0-9]+")) {
            if (t.length() >= 2 && !STOP.contains(t)) out.add(t);
        }
        return out;
    }
}
