package ke.sauti.hms.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ke.sauti.hms.SautiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CryptoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(CryptoConfiguration.class);

    @Test
    void validKeysGiveACipherAndAHasher() {
        runner.withPropertyValues(
                        "sauti.crypto.encryption-key=" + TestKeys.ENCRYPTION, "sauti.crypto.hash-key=" + TestKeys.HASH)
                .run(context -> {
                    assertThat(context).hasSingleBean(ColumnCipher.class);
                    assertThat(context).hasSingleBean(KeyedHasher.class);
                });
    }

    @Test
    void aBadKeyStopsStartup() {
        runner.withPropertyValues("sauti.crypto.encryption-key=c2hvcnQ=", "sauti.crypto.hash-key=" + TestKeys.HASH)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("must decode to 32 bytes"));
    }

    @Test
    void theDemoProfileWillNotStartWithoutBothKeys() {
        SpringApplicationBuilder demo = new SpringApplicationBuilder(SautiApplication.class)
                .profiles("demo")
                .properties(
                        "DATABASE_URL=jdbc:postgresql://localhost/none",
                        "DATABASE_USERNAME=none",
                        "DATABASE_PASSWORD=none");

        assertThatThrownBy(() -> demo.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY, HASH_KEY");
    }
}
