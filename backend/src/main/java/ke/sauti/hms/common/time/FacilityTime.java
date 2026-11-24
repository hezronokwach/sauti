package ke.sauti.hms.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The facility's local time zone ({@code sauti.time-zone}, Africa/Nairobi by default).
 * Instants stay in UTC everywhere; this only answers "which day is it at the clinic",
 * for things that restart each day, like queue numbers.
 */
@Component
public class FacilityTime {

    private final Clock clock;
    private final ZoneId zone;

    public FacilityTime(Clock clock, @Value("${sauti.time-zone:Africa/Nairobi}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZoneId zone() {
        return zone;
    }
}
