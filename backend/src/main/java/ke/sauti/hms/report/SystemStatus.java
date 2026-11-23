package ke.sauti.hms.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import ke.sauti.hms.common.ratelimit.RateLimits;
import ke.sauti.hms.report.dto.BackupStatusResponse;
import ke.sauti.hms.report.dto.FailedJobTypeResponse;
import ke.sauti.hms.report.dto.JobStatusResponse;
import ke.sauti.hms.report.dto.RateLimitBlockResponse;
import ke.sauti.hms.report.dto.SystemStatusResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts and times for the admin status page: jobs, backups and refused requests. */
@Service
public class SystemStatus {

    private static final Duration RECENT = Duration.ofHours(24);
    /** A nightly backup, plus two hours for a slow run. */
    private static final Duration BACKUP_DUE = Duration.ofHours(26);

    private final JdbcTemplate jdbc;
    private final RateLimits limits;
    private final Clock clock;

    public SystemStatus(JdbcTemplate jdbc, RateLimits limits, Clock clock) {
        this.jdbc = jdbc;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemStatusResponse read() {
        Instant now = clock.instant();
        return new SystemStatusResponse(
                jobs(now),
                backup(now),
                limits.blocksSince(now.minus(RECENT)).entrySet().stream()
                        .map(block -> new RateLimitBlockResponse(block.getKey().name(), block.getValue()))
                        .toList());
    }

    private JobStatusResponse jobs(Instant now) {
        Timestamp since = Timestamp.from(now.minus(RECENT));
        List<FailedJobTypeResponse> failed = jdbc.query(
                "select type, count(*) as failures from job where status = 'FAILED' and updated_at >= ?"
                        + " group by type order by failures desc, type",
                (row, number) -> new FailedJobTypeResponse(row.getString("type"), row.getLong("failures")),
                since);
        return Objects.requireNonNull(jdbc.queryForObject(
                """
                select count(*) filter (where status = 'QUEUED') as queued,
                       count(*) filter (where status = 'RUNNING') as running,
                       count(*) filter (where status = 'FAILED' and updated_at >= ?) as failed,
                       min(run_after) filter (where status = 'QUEUED' and run_after <= ?) as oldest
                from job
                """,
                (row, number) -> new JobStatusResponse(
                        row.getLong("queued"),
                        row.getLong("running"),
                        row.getLong("failed"),
                        instant(row, "oldest"),
                        failed),
                since,
                Timestamp.from(now)));
    }

    private BackupStatusResponse backup(Instant now) {
        return Objects.requireNonNull(jdbc.queryForObject("""
                select (select finished_at from backup_run order by started_at desc limit 1) as last_run,
                       (select status from backup_run order by started_at desc limit 1) as last_result,
                       (select max(finished_at) from backup_run where status = 'SUCCESS') as last_success
                """, (row, number) -> {
            Instant lastSuccess = instant(row, "last_success");
            return new BackupStatusResponse(
                    instant(row, "last_run"),
                    row.getString("last_result"),
                    lastSuccess,
                    lastSuccess == null || lastSuccess.isBefore(now.minus(BACKUP_DUE)));
        }));
    }

    private static @Nullable Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
