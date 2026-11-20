package ke.sauti.hms.jobs;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding and cancelling background jobs. Both join the caller's transaction, so a job is
 * queued only if the change that needs it is saved (a piece is stored and its
 * transcription queued together, or neither).
 */
@Component
public class JobQueue {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JobQueue(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Queues a job to run as soon as a worker is free; returns its id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String type, UUID payloadId) {
        return enqueueAt(type, payloadId, clock.instant());
    }

    /** Queues a job to run no earlier than the given time; returns its id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueAt(String type, UUID payloadId, Instant runAfter) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "insert into job (id, type, payload_id, status, attempts, run_after, created_at, updated_at)"
                        + " values (?, ?, ?, 'QUEUED', 0, ?, ?, ?)",
                id,
                type,
                payloadId,
                Timestamp.from(runAfter),
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    /** Cancels this record's jobs that have not started; returns how many. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancel(String type, UUID payloadId) {
        return jdbc.update(
                "update job set status = 'CANCELLED', updated_at = ? where type = ? and payload_id = ?"
                        + " and status = 'QUEUED'",
                Timestamp.from(clock.instant()),
                type,
                payloadId);
    }
}
