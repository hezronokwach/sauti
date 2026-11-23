package ke.sauti.hms.report.dto;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Background work. Error text is left out because it could quote a record.
 *
 * @param queued jobs waiting
 * @param running jobs in progress
 * @param failedLast24Hours jobs that gave up in the last 24 hours
 * @param oldestWaitingSince when the longest-waiting due job became due; null if none
 * @param failedByType the failures per job type
 */
public record JobStatusResponse(
        long queued,
        long running,
        long failedLast24Hours,
        @Nullable Instant oldestWaitingSince,
        List<FailedJobTypeResponse> failedByType) {}
