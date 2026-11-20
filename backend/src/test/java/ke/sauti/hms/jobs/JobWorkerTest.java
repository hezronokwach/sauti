package ke.sauti.hms.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.support.FixedClockConfiguration;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** The job engine on real PostgreSQL, driven step by step on a test clock. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, JobWorkerTest.Handlers.class})
@ActiveProfiles("test")
class JobWorkerTest {

    /** How many times each payload has been run, and how often a flaky one should fail first. */
    static final Map<UUID, AtomicInteger> RUNS = new ConcurrentHashMap<>();

    static final int FLAKY_FAILURES = 2;

    @TestConfiguration(proxyBeanMethods = false)
    static class Handlers {

        @Bean
        JobHandler flaky() {
            return handler("TEST_FLAKY", id -> {
                if (RUNS.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet() <= FLAKY_FAILURES) {
                    throw new IllegalStateException("temporary trouble");
                }
            });
        }

        @Bean
        JobHandler broken() {
            return handler("TEST_BROKEN", id -> {
                RUNS.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
                throw new IllegalStateException("always fails");
            });
        }

        @Bean
        JobHandler permanent() {
            return handler("TEST_PERMANENT", id -> {
                throw new JobFailure.Permanent("the record is gone");
            });
        }

        @Bean
        JobHandler rateLimited() {
            return handler("TEST_RATE_LIMITED", id -> {
                throw new JobFailure.RetryAfter(Duration.ofSeconds(20), "slow down");
            });
        }

        @Bean
        JobHandler noop() {
            return handler("TEST_NOOP", id -> {});
        }

        private static JobHandler handler(String type, Consumer<UUID> work) {
            return new JobHandler() {
                @Override
                public String type() {
                    return type;
                }

                @Override
                public void handle(UUID payloadId) {
                    work.accept(payloadId);
                }
            };
        }
    }

    @Autowired
    private JobWorker worker;

    @Autowired
    private JobQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void clean() {
        clock.set(FixedClockConfiguration.NOW);
        jdbc.update("delete from job");
        RUNS.clear();
    }

    private UUID enqueue(String type) {
        UUID payload = UUID.randomUUID();
        transaction.executeWithoutResult(status -> queue.enqueue(type, payload));
        return payload;
    }

    private Map<String, Object> job(UUID payload) {
        return jdbc.queryForMap(
                "select status, attempts, last_error, run_after from job where payload_id = ?", payload);
    }

    @Test
    void aJobThatFailsTwiceThenSucceedsEndsDone() {
        UUID payload = enqueue("TEST_FLAKY");

        worker.runOnce();
        assertThat(job(payload)).containsEntry("status", "QUEUED").containsEntry("attempts", 1);
        worker.runOnce();
        assertThat(job(payload)).as("not due before its backoff").containsEntry("attempts", 1);

        clock.advance(Duration.ofSeconds(5));
        worker.runOnce();
        assertThat(job(payload)).containsEntry("status", "QUEUED").containsEntry("attempts", 2);

        clock.advance(Duration.ofSeconds(30));
        worker.runOnce();
        assertThat(job(payload)).containsEntry("status", "DONE").containsEntry("attempts", 3);
        assertThat(job(payload).get("last_error")).isNull();
    }

    @Test
    void aPermanentFailureStopsAtOnce() {
        UUID payload = enqueue("TEST_PERMANENT");

        worker.runOnce();

        assertThat(job(payload))
                .containsEntry("status", "FAILED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", "the record is gone");
    }

    @Test
    void aJobFailingEveryTimeStopsAfterFiveAttemptsWithTheBackoffInBetween() {
        UUID payload = enqueue("TEST_BROKEN");

        for (Duration wait : List.of(
                Duration.ZERO,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10))) {
            clock.advance(wait);
            worker.runOnce();
        }

        assertThat(job(payload)).containsEntry("status", "FAILED").containsEntry("attempts", 5);
        assertThat(RUNS.get(payload)).hasValue(5);
        clock.advance(Duration.ofHours(1));
        worker.runOnce();
        assertThat(RUNS.get(payload)).as("never runs again").hasValue(5);
    }

    @Test
    void aRateLimitWaitsTheProvidersDelayWithoutUsingAnAttempt() {
        UUID payload = enqueue("TEST_RATE_LIMITED");

        worker.runOnce();

        Map<String, Object> row = job(payload);
        assertThat(row).containsEntry("status", "QUEUED").containsEntry("attempts", 0);
        assertThat(((Timestamp) row.get("run_after")).toInstant())
                .isEqualTo(FixedClockConfiguration.NOW.plusSeconds(20));
    }

    @Test
    void aJobLeftRunningPastItsLockGoesBackToTheQueue() {
        UUID payload = UUID.randomUUID();
        jdbc.update(
                "insert into job (id, type, payload_id, status, attempts, run_after, locked_until)"
                        + " values (?, 'TEST_NOOP', ?, 'RUNNING', 1, ?, ?)",
                UUID.randomUUID(),
                payload,
                Timestamp.from(FixedClockConfiguration.NOW.minusSeconds(600)),
                Timestamp.from(FixedClockConfiguration.NOW.minusSeconds(1)));

        assertThat(worker.recover()).isEqualTo(1);
        assertThat(job(payload)).containsEntry("status", "QUEUED");

        worker.runOnce();
        assertThat(job(payload)).containsEntry("status", "DONE").containsEntry("attempts", 2);
    }

    @Test
    void aRunningJobWithinItsLockIsLeftAlone() {
        UUID payload = UUID.randomUUID();
        jdbc.update(
                "insert into job (id, type, payload_id, status, attempts, run_after, locked_until)"
                        + " values (?, 'TEST_NOOP', ?, 'RUNNING', 1, ?, ?)",
                UUID.randomUUID(),
                payload,
                Timestamp.from(FixedClockConfiguration.NOW),
                Timestamp.from(FixedClockConfiguration.NOW.plusSeconds(60)));

        assertThat(worker.recover()).isZero();
        assertThat(job(payload)).containsEntry("status", "RUNNING");
    }

    @Test
    void cancelledJobsAreNotRun() {
        UUID payload = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            queue.enqueue("TEST_NOOP", payload);
            assertThat(queue.cancel("TEST_NOOP", payload)).isEqualTo(1);
        });

        assertThat(worker.runOnce()).isZero();
        assertThat(job(payload)).containsEntry("status", "CANCELLED");
    }

    @Test
    void twoWorkersNeverClaimTheSameJob() throws Exception {
        Set<UUID> payloads = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            payloads.add(enqueue("TEST_NOOP"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            futures.add(pool.submit(() -> {
                start.await();
                List<UUID> mine = new ArrayList<>();
                List<JobWorker.ClaimedJob> batch = worker.claim();
                while (!batch.isEmpty()) {
                    batch.forEach(job -> mine.add(job.payloadId()));
                    batch = worker.claim();
                }
                return mine;
            }));
        }
        start.countDown();
        List<UUID> first = futures.get(0).get();
        List<UUID> second = futures.get(1).get();
        pool.shutdown();

        assertThat(Collections.disjoint(first, second)).isTrue();
        Set<UUID> all = new HashSet<>(first);
        all.addAll(second);
        assertThat(all).isEqualTo(payloads);
        assertThat(first.size() + second.size()).isEqualTo(40);
    }
}
