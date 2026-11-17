package vn.com.d2s.smr.service.mt.pipeline.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Vector float32 ⇄ byte[] (little-endian) để lưu cột varbinary. */
public final class VectorCodec {

    private VectorCodec() {
    }

    public static byte[] encode(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) b.putFloat(f);
        return b.array();
    }

    public static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) return null;
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return v;
    }

    public static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    /** Độ giống cosine của hai vector đã chuẩn hóa; khác số chiều → -1. */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return -1;
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += (double) a[i] * b[i];
        return dot;
    }
}
