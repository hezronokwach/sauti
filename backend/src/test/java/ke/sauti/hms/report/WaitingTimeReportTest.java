package ke.sauti.hms.report;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.queue.QueueTicket;
import ke.sauti.hms.queue.QueueTicketService;
import ke.sauti.hms.queue.TicketLane;
import ke.sauti.hms.report.dto.WaitingTimeRow;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The waiting-time report on scripted test visits with known timings. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class WaitingTimeReportTest {

    /** 08:00 in Nairobi, on a day no other test uses. */
    private static final Instant MORNING = Instant.parse("2033-02-14T05:00:00Z");

    private static final LocalDate DAY = LocalDate.ofInstant(MORNING, ZoneId.of("Africa/Nairobi"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private QueueTicketService tickets;

    @Autowired
    private WaitingTimeReport report;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private void minutes(long n) {
        clock.advance(Duration.ofMinutes(n));
    }

    /**
     * One scripted patient through the day: ticket, called after deskWait, sent to triage after
     * registration, taken by the nurse after triageWait, sent on, taken by the doctor after
     * doctorWait (all in minutes).
     */
    private void scripted(TicketLane lane, int deskWait, int registration, int triageWait, int doctorWait) {
        QueueTicket ticket = tickets.issue(lane, new QueueTicketService.Actor(reception.getId(), reception.getRole()));
        minutes(deskWait);
        tickets.callNext(new QueueTicketService.Actor(reception.getId(), reception.getRole()));
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Timing Patient " + UUID.randomUUID(),
                        LocalDate.of(1990, 1, 1),
                        Sex.FEMALE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .getId();
        UUID visitId =
                visits.open(patientId, null, ticket.getId(), actor(reception)).getId();
        minutes(registration);
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        minutes(triageWait);
        visits.take(visitId, actor(nurse));
        minutes(5);
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        minutes(doctorWait);
        visits.take(visitId, actor(doctor));
    }

    @Test
    void eachStageIsMeasuredWithItsMedianAndNinetiethPercentile() {
        clock.set(MORNING);
        scripted(TicketLane.NEW, 4, 6, 10, 20);
        clock.set(MORNING.plus(Duration.ofMinutes(5)));
        scripted(TicketLane.NEW, 8, 4, 20, 30);

        List<WaitingTimeRow> rows = report.between(DAY, DAY);

        assertThat(rows).hasSize(1);
        WaitingTimeRow row = rows.getFirst();
        assertThat(row.lane()).isEqualTo("NEW");
        assertThat(row.hour()).isEqualTo(8);
        assertThat(row.visits()).isEqualTo(2);
        assertThat(row.deskWait()).isEqualTo(new WaitingTimeRow.Stage(6.0, 7.6));
        assertThat(row.registration()).isEqualTo(new WaitingTimeRow.Stage(5.0, 5.8));
        assertThat(row.triageWait()).isEqualTo(new WaitingTimeRow.Stage(15.0, 19.0));
        assertThat(row.doctorWait()).isEqualTo(new WaitingTimeRow.Stage(25.0, 29.0));
        // Door to doctor: 4+6+10+5+20 = 45 and 8+4+20+5+30 = 67.
        assertThat(row.doorToDoctor()).isEqualTo(new WaitingTimeRow.Stage(56.0, 64.8));
    }

    @Test
    void lanesAndHoursAreReportedSeparatelyAndAStageNotReachedIsEmpty() {
        clock.set(MORNING.plus(Duration.ofDays(1)));
        scripted(TicketLane.RETURNING, 2, 3, 4, 5);
        clock.set(MORNING.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)));
        QueueTicket waiting =
                tickets.issue(TicketLane.NEW, new QueueTicketService.Actor(reception.getId(), reception.getRole()));
        tickets.callNext(new QueueTicketService.Actor(reception.getId(), reception.getRole()));
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Still At Reception",
                        LocalDate.of(1990, 1, 1),
                        Sex.MALE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .getId();
        visits.open(patientId, null, waiting.getId(), actor(reception));

        List<WaitingTimeRow> rows = report.between(DAY.plusDays(1), DAY.plusDays(1));

        assertThat(rows)
                .extracting(WaitingTimeRow::lane, WaitingTimeRow::hour)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("NEW", 10),
                        org.assertj.core.groups.Tuple.tuple("RETURNING", 8));
        assertThat(rows.getFirst().registration().median()).isNull();
        assertThat(rows.getFirst().doorToDoctor().median()).isNull();
    }

    @Test
    void onlyTheAdministratorSeesTheReportAndLongRangesAreRefused() throws Exception {
        Staff admin = ActingAs.staff(staffRepository, StaffRole.ADMIN);

        mvc.perform(get("/api/admin/reports/waiting-times")
                        .param("from", DAY.toString())
                        .param("to", DAY.toString())
                        .with(as(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/waiting-times").with(as(reception))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/waiting-times")
                        .param("from", "2033-01-01")
                        .param("to", "2033-06-01")
                        .with(as(admin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Choose at most 92 days"));
    }
}
