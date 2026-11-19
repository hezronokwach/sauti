package ke.sauti.hms.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Import into a test to make every injected {@link Clock} read {@link #NOW}. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final Instant NOW = Instant.parse("2026-01-01T08:00:00Z");

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
