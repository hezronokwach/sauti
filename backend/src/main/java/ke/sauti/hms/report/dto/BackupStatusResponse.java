package ke.sauti.hms.report.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The nightly backup.
 *
 * @param lastRunAt when the newest attempt finished; null if none has run
 * @param lastResult SUCCESS or FAILED; null if none has run
 * @param lastSuccessAt when the newest good backup finished; null if there is none
 * @param overdue true when there has been no good backup in the last 26 hours
 */
public record BackupStatusResponse(
        @Nullable Instant lastRunAt,
        @Nullable String lastResult,
        @Nullable Instant lastSuccessAt,
        boolean overdue) {}
