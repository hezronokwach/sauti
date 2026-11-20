package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** Audio in real S3-compatible storage (SeaweedFS, as in development) is always encrypted. */
@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AudioStoreTest {

    @Container
    static final GenericContainer<?> SEAWEED = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.54"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/")
                    .forPort(8333)
                    .forStatusCodeMatching(code -> code > 0)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("sauti.storage.endpoint", () -> "http://" + SEAWEED.getHost() + ":" + SEAWEED.getMappedPort(8333));
        registry.add("sauti.storage.bucket", () -> "test-audio");
        registry.add("sauti.storage.create-bucket", () -> "true");
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
