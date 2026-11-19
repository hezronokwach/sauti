package ke.sauti.hms.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * HMAC-SHA256 under {@code HASH_KEY}, for exact lookups of encrypted values (a phone
 * number, a national ID) without decrypting every row. Callers normalise the value
 * first (for example strip spaces from a phone number) so equal values hash equally.
 */
public class KeyedHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKey key;

    public KeyedHasher(CryptoKeys keys) {
        this.key = keys.hashKey();
    }

    /** Returns the 32-byte hash of the value's UTF-8 bytes. */
    public byte[] hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
