package ke.sauti.hms.jobs;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs background jobs. Every second it claims up to {@value #BATCH} due jobs in one
 * statement ({@code for update skip locked}, so two workers never take the same job),
 * runs each handler, and records the outcome:
 * <ul>
 *   <li>success: DONE
 *   <li>{@link JobFailure.RetryAfter}: queued again after the provider's delay, not counted as an attempt
 *   <li>{@link JobFailure.Permanent}: FAILED
 *   <li>anything else: queued again after 5 s, 30 s, 2 min, 10 min; FAILED after {@value #MAX_ATTEMPTS} attempts
 * </ul>
 * Every minute, jobs left RUNNING past their lock (a crash mid-job) go back to the queue.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    static final int BATCH = 5;
    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK = Duration.ofMinutes(5);
    static final List<Duration> BACKOFF =
            List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));
    private static final int MAX_ERROR = 500;

    private static final String CLAIM = "update job set status = 'RUNNING', locked_until = ?, attempts = attempts + 1,"
            + " updated_at = ? where id in (select id from job where status = 'QUEUED' and run_after <= ?"
            + " order by run_after limit ? for update skip locked) returning id, type, payload_id, attempts";

    /** A job this worker has claimed. */
    record ClaimedJob(UUID id, String type, UUID payloadId, int attempts) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Map<String, JobHandler> handlers;
    private final Clock clock;
    private final boolean enabled;

    public JobWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            List<JobHandler> handlers,
            Clock clock,
            @Value("${sauti.jobs.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${sauti.jobs.poll-ms:1000}")
    public void poll() {
        if (enabled) {
            runOnce();
        }
    }

    @Scheduled(fixedDelayString = "${sauti.jobs.recover-ms:60000}")
    public void scheduledRecover() {
        if (enabled) {
            recover();
        }
    }

    /** Claims and runs one batch; returns how many jobs it ran. */
    public int runOnce() {
        List<ClaimedJob> claimed = claim();
        claimed.forEach(this::run);
        return claimed.size();
    }

    /** Claims up to one batch of due jobs, marking them RUNNING with a lock. */
    List<ClaimedJob> claim() {
        Instant now = clock.instant();
        List<ClaimedJob> claimed = transaction.execute(status -> jdbc.query(
                CLAIM,
                (rs, row) -> new ClaimedJob(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getObject("payload_id", UUID.class),
                        rs.getInt("attempts")),
                Timestamp.from(now.plus(LOCK)),
                Timestamp.from(now),
                Timestamp.from(now),
                BATCH));
        return claimed;
    }

    /** Returns jobs whose lock expired while RUNNING to the queue; returns how many. */
    public int recover() {
        Timestamp now = Timestamp.from(clock.instant());
        int recovered = transaction.execute(status -> jdbc.update(
                "update job set status = 'QUEUED', locked_until = null, run_after = ?, updated_at = ?"
                        + " where status = 'RUNNING' and locked_until < ?",
                now,
                now,
                now));
        if (recovered > 0) {
            log.warn("Returned {} stuck job(s) to the queue", recovered);
        }
        return recovered;
    }

    private void run(ClaimedJob job) {
        JobHandler handler = handlers.get(job.type());
        if (handler == null) {
            fail(job, "No handler for job type " + job.type());
            return;
        }
        try {
            handler.handle(job.payloadId());
            finish(job, "DONE", null, null, job.attempts());
        } catch (JobFailure.RetryAfter e) {
            log.info("Job {} {} asked to wait {}", job.type(), job.id(), e.getDelay());
            finish(job, "QUEUED", clock.instant().plus(e.getDelay()), e.getMessage(), job.attempts() - 1);
        } catch (JobFailure.Permanent e) {
            fail(job, e.getMessage());
        } catch (Exception e) {
            if (job.attempts() >= MAX_ATTEMPTS) {
                fail(job, describe(e));
            } else {
                Duration wait = BACKOFF.get(job.attempts() - 1);
                log.warn("Job {} {} failed (attempt {}), retrying in {}", job.type(), job.id(), job.attempts(), wait);
                finish(job, "QUEUED", clock.instant().plus(wait), describe(e), job.attempts());
            }
        }
    }

    private void fail(ClaimedJob job, String error) {
        log.error("Job {} {} failed permanently after {} attempt(s): {}", job.type(), job.id(), job.attempts(), error);
        finish(job, "FAILED", null, error, job.attempts());
    }

    private void finish(ClaimedJob job, String status, Instant runAfter, String error, int attempts) {
        Timestamp now = Timestamp.from(clock.instant());
        transaction.executeWithoutResult(tx -> jdbc.update(
                "update job set status = ?, locked_until = null, run_after = coalesce(?, run_after), last_error = ?,"
                        + " attempts = ?, updated_at = ? where id = ? and status = 'RUNNING'",
                status,
                runAfter == null ? null : Timestamp.from(runAfter),
                truncate(error),
                Math.max(attempts, 0),
                now,
                job.id()));
    }

    private static String describe(Exception e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR);
    }
}
