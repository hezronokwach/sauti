package ke.sauti.hms.support;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * One SeaweedFS container (S3-compatible, as in development) shared by every test that
 * stores audio. Started on first use, stopped when the test JVM exits.
 */
public final class SeaweedFs {

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>(
                    DockerImageName.parse("chrislusf/seaweedfs:4.54"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333")
            .withExposedPorts(8333)
            .waitingFor(Wait.forHttp("/")
                    .forPort(8333)
                    .forStatusCodeMatching(code -> code > 0)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private SeaweedFs() {}

    /** Points the app's audio storage at the container; call from a @DynamicPropertySource. */
    public static void register(DynamicPropertyRegistry registry, String bucket) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        registry.add(
                "sauti.storage.endpoint", () -> "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(8333));
        registry.add("sauti.storage.bucket", () -> bucket);
        registry.add("sauti.storage.create-bucket", () -> "true");
    }
}
