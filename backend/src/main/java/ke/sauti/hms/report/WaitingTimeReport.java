package ke.sauti.hms.report;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.time.FacilityTime;
import ke.sauti.hms.report.dto.WaitingTimeRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where patients wait, measured, so improvements can be shown and not just claimed. Each
 * stage is read from the times the system already records: the ticket (arrival and call),
 * the visit's registration times, and the first time the visit entered each status. Only
 * timings leave the database: no names, no clinical data.
 */
@Service
public class WaitingTimeReport {

    /** One query: each visit's stage times, then the median and 90th percentile per lane and hour. */
    private static final String REPORT = """
            with stages as (
              select coalesce(t.lane, 'NO_TICKET') as lane,
                     extract(hour from v.arrived_at at time zone ?)::int as hour,
                     v.arrived_at,
                     v.registration_started_at,
                     v.registration_completed_at,
                     (select min(h.changed_at) from visit_status_history h
                       where h.visit_id = v.id and h.to_status = 'WAITING_TRIAGE') as waiting_triage,
                     (select min(h.changed_at) from visit_status_history h
                       where h.visit_id = v.id and h.to_status = 'IN_TRIAGE') as in_triage,
                     (select min(h.changed_at) from visit_status_history h
                       where h.visit_id = v.id and h.to_status = 'WAITING_DOCTOR') as waiting_doctor,
                     (select min(h.changed_at) from visit_status_history h
                       where h.visit_id = v.id and h.to_status = 'WITH_DOCTOR') as with_doctor
                from visit v
                left join queue_ticket t on t.visit_id = v.id
               where v.visit_date between ? and ?
            ),
            minutes as (
              select lane, hour,
                     extract(epoch from registration_started_at - arrived_at) / 60 as desk,
                     extract(epoch from registration_completed_at - registration_started_at) / 60 as registration,
                     extract(epoch from in_triage - waiting_triage) / 60 as triage,
                     extract(epoch from with_doctor - waiting_doctor) / 60 as doctor,
                     extract(epoch from with_doctor - arrived_at) / 60 as door
                from stages
            )
            select lane, hour, count(*) as visits,
                   percentile_cont(0.5) within group (order by desk) as desk_median,
                   percentile_cont(0.9) within group (order by desk) as desk_p90,
                   percentile_cont(0.5) within group (order by registration) as registration_median,
                   percentile_cont(0.9) within group (order by registration) as registration_p90,
                   percentile_cont(0.5) within group (order by triage) as triage_median,
                   percentile_cont(0.9) within group (order by triage) as triage_p90,
                   percentile_cont(0.5) within group (order by doctor) as doctor_median,
                   percentile_cont(0.9) within group (order by doctor) as doctor_p90,
                   percentile_cont(0.5) within group (order by door) as door_median,
                   percentile_cont(0.9) within group (order by door) as door_p90
              from minutes
             group by lane, hour
             order by lane, hour
            """;

    /** Longer ranges make a slow query and say little per hour. */
    static final int MAX_DAYS = 92;

    private final JdbcTemplate jdbc;
    private final FacilityTime facilityTime;

    public WaitingTimeReport(JdbcTemplate jdbc, FacilityTime facilityTime) {
        this.jdbc = jdbc;
        this.facilityTime = facilityTime;
    }

    @Transactional(readOnly = true)
    public List<WaitingTimeRow> between(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("The end date is before the start date");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BusinessRuleException("Choose at most " + MAX_DAYS + " days");
        }
        return jdbc.query(
                REPORT,
                (rs, row) -> new WaitingTimeRow(
                        rs.getString("lane"),
                        rs.getInt("hour"),
                        rs.getInt("visits"),
                        stage(rs, "desk"),
                        stage(rs, "registration"),
                        stage(rs, "triage"),
                        stage(rs, "doctor"),
                        stage(rs, "door")),
                facilityTime.zone().getId(),
                Date.valueOf(from),
                Date.valueOf(to));
    }

    private static WaitingTimeRow.Stage stage(ResultSet rs, String name) throws SQLException {
        return new WaitingTimeRow.Stage(minutes(rs, name + "_median"), minutes(rs, name + "_p90"));
    }

    private static Double minutes(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : Math.round(value * 10) / 10.0;
    }
}
