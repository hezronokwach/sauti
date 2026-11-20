package ke.sauti.hms.queue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two ticket statements that must be single SQL statements to be safe with several
 * reception desks: the next number in a lane, and choosing the next ticket to call.
 */
@Component
public class TicketSql {

    private static final String NEXT_NUMBER = "insert into queue_ticket_counter (day, lane, last_number)"
            + " values (?, ?, 1)"
            + " on conflict (day, lane) do update set last_number = queue_ticket_counter.last_number + 1"
            + " returning last_number";

    /**
     * Today's next ticket for reception, locked: a new ticket waiting 15 minutes or more
     * first, then pre-registered and returning patients, then new patients, each in arrival
     * order. Emergencies never come through here. SKIP LOCKED lets two desks call at the
     * same moment and get different tickets.
     */
    private static final String NEXT_TO_CALL = "select id from queue_ticket"
            + " where ticket_date = ? and status = 'WAITING' and lane <> 'EMERGENCY'"
            + " order by case"
            + "   when lane = 'NEW' and arrived_at <= ? then 0"
            + "   when lane in ('PRE_REGISTERED', 'RETURNING') then 1"
            + "   else 2 end,"
            + " arrived_at, number"
            + " limit 1 for update skip locked";

    private final JdbcTemplate jdbc;

    TicketSql(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    int nextNumber(LocalDate day, TicketLane lane) {
        return Objects.requireNonNull(jdbc.queryForObject(NEXT_NUMBER, Integer.class, day, lane.name()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<UUID> lockNextToCall(LocalDate day, Instant overdueBefore) {
        List<UUID> ids = jdbc.queryForList(NEXT_TO_CALL, UUID.class, day, Timestamp.from(overdueBefore));
        return ids.stream().findFirst();
    }
}
