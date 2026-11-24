package ke.sauti.hms.jobs;

import java.util.UUID;

/**
 * Does one kind of background work. Implementations are Spring beans; the worker finds
 * them by {@link #type()}.
 *
 * <p><strong>Every handler must be safe to run twice.</strong> A worker can stop after the
 * work is done but before the job is marked DONE (a crash, a deploy), and the recovery task
 * then runs it again. For example, the transcription handler deletes a piece's old segments
 * before inserting new ones.
 *
 * <p>Outcomes: return normally for success; throw {@link JobFailure.RetryAfter} when a
 * provider asks to wait (rate limit), {@link JobFailure.Permanent} when retrying cannot
 * help (the record is gone, the input is invalid). Anything else is a temporary failure
 * and is retried with backoff.
 */
public interface JobHandler {

    /** The job type this handler runs, for example TRANSCRIBE. At most 40 characters. */
    String type();

    /** Runs the job for one record. Must be safe to run twice. */
    void handle(UUID payloadId) throws Exception;
}
