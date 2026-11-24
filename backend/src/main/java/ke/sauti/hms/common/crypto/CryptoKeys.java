package ke.sauti.hms.common.crypto;

import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * The decoded encryption and hash keys. Decoding fails, and with it startup, when a key
 * is missing, is not Base64, is not exactly 32 bytes, or when both keys are the same:
 * one key doing both jobs would weaken both.
 */
public final class CryptoKeys {

    static final int KEY_BYTES = 32;

    private final SecretKey encryptionKey;
    private final SecretKey hashKey;

    private CryptoKeys(SecretKey encryptionKey, SecretKey hashKey) {
        this.encryptionKey = encryptionKey;
        this.hashKey = hashKey;
    }

    public static CryptoKeys from(CryptoProperties properties) {
        byte[] encryption = decode("sauti.crypto.encryption-key (APP_ENCRYPTION_KEY)", properties.encryptionKey());
        byte[] hash = decode("sauti.crypto.hash-key (HASH_KEY)", properties.hashKey());
        if (MessageDigest.isEqual(encryption, hash)) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY and HASH_KEY must be different keys");
        }
        return new CryptoKeys(new SecretKeySpec(encryption, "AES"), new SecretKeySpec(hash, "HmacSHA256"));
    }

    private static byte[] decode(String name, @Nullable String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " is not valid Base64", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException(name + " must decode to " + KEY_BYTES + " bytes, not " + key.length
                    + "; make one with: openssl rand -base64 32");
        }
        return key;
    }

    SecretKey encryptionKey() {
        return encryptionKey;
    }

    SecretKey hashKey() {
        return hashKey;
    }
}
