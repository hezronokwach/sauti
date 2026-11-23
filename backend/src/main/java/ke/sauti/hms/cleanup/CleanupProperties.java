package ke.sauti.hms.cleanup;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How long things are kept. Confirm against the legal schedule before real use.
 *
 * @param audioRetention how long audio is kept after the visit's note is signed
 * @param orphanGrace how old a file with no row must be before it is deleted
 */
@Validated
@ConfigurationProperties(prefix = "sauti.cleanup")
public record CleanupProperties(
        @NotNull Duration audioRetention, @NotNull Duration orphanGrace) {}
