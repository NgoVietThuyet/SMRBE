package vn.com.d2s.smr.service.ad.face;

import org.springframework.stereotype.Component;
import vn.com.d2s.smr.config.FaceProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Mã hóa đặc trưng khuôn mặt trước khi ghi DB: AES-256-GCM, nonce ngẫu nhiên 12 byte, tag 128 bit.
 * Bản ghi = nonce | ciphertext | tag. Tên đăng nhập là AAD nên không chép được mẫu của người này sang người khác.
 */
@Component
public class FaceTemplateCrypto {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FaceTemplateCrypto(FaceProperties props) {
        SecretKeySpec k = null;
        String raw = props.encKey();
        if (raw != null && !raw.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(raw.trim());
                if (bytes.length == 32) k = new SecretKeySpec(bytes, "AES");
            } catch (IllegalArgumentException ignored) {
                // khóa sai định dạng → coi như chưa cấu hình
            }
        }
        this.key = k;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public byte[] encrypt(float[] embedding, String userName) {
        requireKey();
        ByteBuffer plain = ByteBuffer.allocate(embedding.length * 4);
        for (float f : embedding) plain.putFloat(f);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            c.updateAAD(aad(userName));
            byte[] enc = c.doFinal(plain.array());
            return ByteBuffer.allocate(nonce.length + enc.length).put(nonce).put(enc).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hóa được mẫu khuôn mặt.", e);
        }
    }

    public float[] decrypt(byte[] blob, String userName) {
        requireKey();
        if (blob == null || blob.length <= NONCE_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException("Mẫu khuôn mặt không hợp lệ.");
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, NONCE_BYTES));
            c.updateAAD(aad(userName));
            byte[] plain = c.doFinal(blob, NONCE_BYTES, blob.length - NONCE_BYTES);
            ByteBuffer buf = ByteBuffer.wrap(plain);
            float[] out = new float[plain.length / 4];
            for (int i = 0; i < out.length; i++) out[i] = buf.getFloat();
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không giải mã được mẫu khuôn mặt (sai khóa hoặc dữ liệu bị sửa).", e);
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("Chưa cấu hình FACE_ENC_KEY (32 byte base64).");
    }

    private static byte[] aad(String userName) {
        return userName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }
}
