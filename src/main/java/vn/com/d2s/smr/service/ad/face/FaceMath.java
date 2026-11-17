package vn.com.d2s.smr.service.ad.face;

import java.util.List;

/** Vector đặc trưng: chuẩn hóa độ dài 1 để độ giống cosine = tích vô hướng. */
public final class FaceMath {

    private FaceMath() {
    }

    public static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0) return v.clone();
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    /** Độ giống cosine của hai vector đã chuẩn hóa. Khác số chiều → -1 (không bao giờ khớp). */
    public static double similarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return -1;
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += (double) a[i] * b[i];
        return dot;
    }

    /** Trung bình rồi chuẩn hóa. */
    public static float[] mean(List<float[]> vectors) {
        float[] sum = new float[vectors.get(0).length];
        for (float[] v : vectors) {
            for (int i = 0; i < sum.length; i++) sum[i] += v[i];
        }
        return normalize(sum);
    }
}
