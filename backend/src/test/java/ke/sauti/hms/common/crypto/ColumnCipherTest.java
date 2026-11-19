package ke.sauti.hms.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColumnCipherTest {

    private final ColumnCipher cipher = new ColumnCipher(TestKeys.keys());

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "JBSWY3DPEHPK3PXP", "Mgonjwa ana homa na kikohozi kwa siku tatu 🤒"})
    void decryptsWhatItEncrypted(String text) {
        byte[] stored = cipher.encrypt(utf8(text));

        assertThat(new String(cipher.decrypt(stored), StandardCharsets.UTF_8)).isEqualTo(text);
    }

    @Test
    void storesTheVersionNonceAndTag() {
        byte[] stored = cipher.encrypt(utf8("secret"));

        assertThat(stored[0]).isEqualTo(ColumnCipher.KEY_VERSION);
        assertThat(stored).hasSize(1 + ColumnCipher.NONCE_BYTES + "secret".length() + ColumnCipher.TAG_BITS / 8);
    }

    @Test
    void theSameTextEncryptsDifferentlyEachTime() {
        byte[] first = cipher.encrypt(utf8("same text"));
        byte[] second = cipher.encrypt(utf8("same text"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theStoredBytesDoNotContainTheText() {
        byte[] stored = cipher.encrypt(utf8("JBSWY3DPEHPK3PXP"));

        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("JBSWY3DPEHPK3PXP");
    }

    /** Positions in a 35-byte value for "secret": nonce start and end, ciphertext start and middle, tag start and end. */
    @ParameterizedTest
    @ValueSource(ints = {1, 12, 13, 15, 19, 34})
    void anEditedByteFailsTheTamperCheck(int position) {
        byte[] stored = cipher.encrypt(utf8("secret"));
        stored[position] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(stored))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("tamper check")
                .hasMessageNotContaining("secret");
    }

    @Test
    void anEditedVersionByteIsRejected() {
        byte[] stored = cipher.encrypt(utf8("secret"));
        stored[0] = 2;

        assertThatThrownBy(() -> cipher.decrypt(stored))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("Unknown encryption key version 2");
    }

    @Test
    void aWrongKeyFailsTheTamperCheck() {
        byte[] stored = cipher.encrypt(utf8("secret"));
        ColumnCipher otherKey = new ColumnCipher(TestKeys.otherEncryptionKey());

        assertThatThrownBy(() -> otherKey.decrypt(stored))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("tamper check");
    }

    @Test
    void truncatedBytesAreRejected() {
        byte[] stored = cipher.encrypt(utf8(""));
        byte[] truncated = Arrays.copyOf(stored, stored.length - 1);

        assertThatThrownBy(() -> cipher.decrypt(truncated))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("too short");
        assertThatThrownBy(() -> cipher.decrypt(new byte[0])).isInstanceOf(CryptoException.class);
    }
}
