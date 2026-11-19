package ke.sauti.hms.common.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM encryption for sensitive values. Stored layout:
 * {@code [key version, 1 byte][nonce, 12 bytes][ciphertext + 16-byte tamper tag]}.
 * <p>
 * Every call uses a fresh random nonce, so the same text never encrypts to the same
 * bytes twice. The version byte is also fed to the tamper check, so changing it is
 * detected. Only version 1 exists; rotating keys later means keeping old keys by
 * version for decryption, without changing this layout.
 */
public class ColumnCipher {

    static final byte KEY_VERSION = 1;
    static final int NONCE_BYTES = 12;
    static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int HEADER_BYTES = 1 + NONCE_BYTES;
    private static final int MIN_LENGTH = HEADER_BYTES + TAG_BITS / 8;

    private final SecretKey key;
    private final SecureRandom random;

    public ColumnCipher(CryptoKeys keys) {
        this.key = keys.encryptionKey();
        this.random = new SecureRandom();
    }

    public byte[] encrypt(byte[] plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(new byte[] {KEY_VERSION});
            byte[] sealed = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(HEADER_BYTES + sealed.length)
                    .put(KEY_VERSION)
                    .put(nonce)
                    .put(sealed)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption is unavailable", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (stored.length < MIN_LENGTH) {
            throw new CryptoException("Encrypted value is too short (" + stored.length + " bytes)");
        }
        byte version = stored[0];
        if (version != KEY_VERSION) {
            throw new CryptoException("Unknown encryption key version " + version);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, NONCE_BYTES));
            cipher.updateAAD(stored, 0, 1);
            return cipher.doFinal(stored, HEADER_BYTES, stored.length - HEADER_BYTES);
        } catch (AEADBadTagException e) {
            throw new CryptoException("Encrypted value failed its tamper check (wrong key or edited bytes)", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encrypted value could not be decrypted", e);
        }
    }
}
