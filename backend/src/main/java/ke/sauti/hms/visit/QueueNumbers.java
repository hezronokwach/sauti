package ke.sauti.hms.visit;

import java.time.LocalDate;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The day's next queue number, in one statement: the first visit of the day creates the
 * counter row, later ones increment it under the row lock PostgreSQL takes for the update,
 * so no two visits get the same number. Runs in the caller's transaction, so a visit that
 * fails to open does not use up a number.
 */
@Component
public class QueueNumbers {

    private static final String NEXT = "insert into queue_counter (day, last_number) values (?, 1)"
            + " on conflict (day) do update set last_number = queue_counter.last_number + 1"
            + " returning last_number";

    private final JdbcTemplate jdbc;

    public QueueNumbers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(LocalDate day) {
        return Objects.requireNonNull(jdbc.queryForObject(NEXT, Integer.class, day));
    }
}
