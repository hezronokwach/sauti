package ke.sauti.hms.cleanup;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import ke.sauti.hms.capture.AudioRetention;
import ke.sauti.hms.prereg.PreRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The nightly clean-up: old audio files, unused pre-registrations and stored files no row points
 * to. It never deletes notes, prescriptions, vitals, transcripts or audit rows. One step failing
 * does not stop the others, and every step is safe to run twice.
 */
@Component
@EnableConfigurationProperties(CleanupProperties.class)
public class NightlyCleanup {

    private static final Logger log = LoggerFactory.getLogger(NightlyCleanup.class);

    private final AudioRetention audio;
    private final PreRegistrationService preRegistrations;
    private final CleanupProperties properties;
    private final Clock clock;
    private final boolean enabled;

    public NightlyCleanup(
            AudioRetention audio,
            PreRegistrationService preRegistrations,
            CleanupProperties properties,
            Clock clock,
            @Value("${sauti.jobs.enabled:true}") boolean enabled) {
        this.audio = audio;
        this.preRegistrations = preRegistrations;
        this.properties = properties;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${sauti.cleanup.cron}", zone = "${sauti.time-zone:Africa/Nairobi}")
    public void scheduled() {
        if (enabled) {
            run();
        }
    }

    /** Runs every step once. */
    public void run() {
        Instant now = clock.instant();
        step("audio", () -> audio.eraseAudioOfNotesSignedBefore(now.minus(properties.audioRetention())));
        step("pre-registrations", preRegistrations::deleteFromEarlierDays);
        step("orphaned files", () -> audio.deleteOrphansOlderThan(now.minus(properties.orphanGrace())));
    }

    private static void step(String name, Supplier<Integer> work) {
        try {
            log.info("Clean-up of {} removed {}", name, work.get());
        } catch (RuntimeException e) {
            log.error("Clean-up of {} failed; it will run again tomorrow", name, e);
        }
    }
}
