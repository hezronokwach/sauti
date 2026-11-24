package ke.sauti.hms.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the application reads time from. Inject {@link Clock} instead of
 * calling {@code Instant.now()} directly, so tests can replace it with a fixed one.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
