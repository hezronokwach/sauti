package ke.sauti.hms.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoKeysTest {

    @Test
    void acceptsTwoDifferent32ByteKeys() {
        assertThat(TestKeys.keys()).isNotNull();
    }

    @Test
    void rejectsAMissingKey() {
        assertThatThrownBy(() -> CryptoKeys.from(new CryptoProperties(null, TestKeys.HASH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY")
                .hasMessageContaining("not set");
        assertThatThrownBy(() -> CryptoKeys.from(new CryptoProperties(TestKeys.ENCRYPTION, " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HASH_KEY");
    }

    @Test
    void rejectsAKeyThatIsNotBase64() {
        assertThatThrownBy(() -> CryptoKeys.from(new CryptoProperties("not base64 at all!", TestKeys.HASH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> CryptoKeys.from(new CryptoProperties(sixteenBytes, TestKeys.HASH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes, not 16");
    }

    @Test
    void rejectsTheSameKeyForBothJobs() {
        assertThatThrownBy(() -> CryptoKeys.from(new CryptoProperties(TestKeys.ENCRYPTION, TestKeys.ENCRYPTION)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void errorMessagesNeverContainTheKey() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> CryptoKeys.from(new CryptoProperties(sixteenBytes, TestKeys.HASH)))
                .hasMessageNotContaining(sixteenBytes);
    }
}
