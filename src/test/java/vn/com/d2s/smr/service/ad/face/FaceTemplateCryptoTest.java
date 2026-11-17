package vn.com.d2s.smr.service.ad.face;

import org.junit.jupiter.api.Test;
import vn.com.d2s.smr.config.FaceProperties;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaceTemplateCryptoTest {

    static String key(int fill) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, (byte) fill);
        return Base64.getEncoder().encodeToString(k);
    }

    static FaceProperties props(String encKey) {
        return new FaceProperties(true, "http://x", "k", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, encKey);
    }

    @Test
    void roundTripKeepsEmbedding() {
        FaceTemplateCrypto crypto = new FaceTemplateCrypto(props(key(7)));
        float[] v = {0.12f, -0.83f, 0.45f, 0.07f};

        byte[] blob = crypto.encrypt(v, "Alice");

        assertThat(blob).hasSize(12 + v.length * 4 + 16);
        assertThat(crypto.decrypt(blob, "alice")).containsExactly(v);
    }

    @Test
    void sameEmbeddingEncryptsDifferentlyEveryTime() {
        FaceTemplateCrypto crypto = new FaceTemplateCrypto(props(key(7)));
        float[] v = {1f, 2f, 3f};
        assertThat(crypto.encrypt(v, "a")).isNotEqualTo(crypto.encrypt(v, "a"));
    }

    @Test
    void tamperedDataWrongKeyOrWrongUserFails() {
        FaceTemplateCrypto crypto = new FaceTemplateCrypto(props(key(7)));
        byte[] blob = crypto.encrypt(new float[]{1f, 2f}, "alice");

        byte[] tampered = blob.clone();
        tampered[14] ^= 0x01;
        assertThatThrownBy(() -> crypto.decrypt(tampered, "alice")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(blob, "bob")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FaceTemplateCrypto(props(key(9))).decrypt(blob, "alice")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notConfiguredWithoutValidKey() {
        assertThat(new FaceTemplateCrypto(props("")).isConfigured()).isFalse();
        assertThat(new FaceTemplateCrypto(props("not-base64!!")).isConfigured()).isFalse();
        assertThat(new FaceTemplateCrypto(props(Base64.getEncoder().encodeToString(new byte[16]))).isConfigured()).isFalse();
        assertThatThrownBy(() -> new FaceTemplateCrypto(props("")).encrypt(new float[]{1f}, "a")).isInstanceOf(IllegalStateException.class);
    }
}
