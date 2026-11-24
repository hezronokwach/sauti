package ke.sauti.hms.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Import into a test to make every injected clock a {@link MutableClock} starting at {@link FixedClockConfiguration#NOW}. */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(FixedClockConfiguration.NOW);
    }
}
