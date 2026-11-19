package ke.sauti.hms.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class KeyedHasherTest {

    private final KeyedHasher hasher = new KeyedHasher(TestKeys.keys());

    @Test
    void matchesAnIndependentlyComputedHmacSha256() {
        // python: hmac.new(base64.b64decode(TestKeys.HASH), b"254712345678", hashlib.sha256).hexdigest()
        assertThat(HexFormat.of().formatHex(hasher.hash("254712345678")))
                .isEqualTo("6f7adf6fb9c053f4073ab243391389c3cf9cddfc07646ab5bf854131ee284bfd");
    }

    @Test
    void theSameValueAlwaysHashesTheSame() {
        assertThat(hasher.hash("254712345678"))
                .isEqualTo(hasher.hash("254712345678"))
                .hasSize(32);
    }

    @Test
    void differentValuesHashDifferently() {
        assertThat(hasher.hash("254712345678")).isNotEqualTo(hasher.hash("254712345679"));
    }

    @Test
    void aDifferentKeyGivesADifferentHash() {
        KeyedHasher otherKey = new KeyedHasher(TestKeys.otherHashKey());

        assertThat(otherKey.hash("254712345678")).isNotEqualTo(hasher.hash("254712345678"));
    }
}
