package ke.sauti.hms.common.crypto;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The two keys, each 32 random bytes in Base64, read from {@code APP_ENCRYPTION_KEY}
 * and {@code HASH_KEY}. They stay as text here; {@link CryptoKeys} checks and decodes them.
 *
 * @param encryptionKey key for {@link ColumnCipher}
 * @param hashKey separate key for {@link KeyedHasher}
 */
@ConfigurationProperties(prefix = "sauti.crypto")
public record CryptoProperties(
        @Nullable String encryptionKey, @Nullable String hashKey) {}
