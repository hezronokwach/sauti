package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.support.SeaweedFs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** Audio in real S3-compatible storage (SeaweedFS, as in development) is always encrypted. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AudioStoreTest {

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        SeaweedFs.register(registry, "test-audio");
    }

    @Autowired
    private AudioStore store;

    private static final byte[] AUDIO = "RIFF....WAVEfmt fake audio: mgonjwa ana homa".getBytes(StandardCharsets.UTF_8);

    @Test
    void whatGoesInComesBackOut() {
        String key = "sessions/" + UUID.randomUUID() + "/000001";

        store.put(key, AUDIO);

        assertThat(store.get(key)).isEqualTo(AUDIO);
    }

    @Test
    void theBucketHoldsOnlyUnreadableBytes() {
        String key = "sessions/" + UUID.randomUUID() + "/000001";

        store.put(key, AUDIO);

        byte[] raw = store.getRaw(key);
        assertThat(raw).isNotEqualTo(AUDIO);
        assertThat(new String(raw, StandardCharsets.ISO_8859_1))
                .doesNotContain("homa")
                .doesNotContain("RIFF");
        assertThat(raw[0]).as("key version byte").isEqualTo((byte) 1);
    }

    @Test
    void aDeletedPieceIsGone() {
        String key = "sessions/" + UUID.randomUUID() + "/000001";
        store.put(key, AUDIO);

        store.delete(key);

        assertThatThrownBy(() -> store.get(key)).isInstanceOf(NoSuchKeyException.class);
    }
}
