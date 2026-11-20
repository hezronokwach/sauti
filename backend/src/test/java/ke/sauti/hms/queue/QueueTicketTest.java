package ke.sauti.hms.queue;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.ApiBrowser;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
import ke.sauti.hms.visit.VisitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Arrival tickets: numbering, fair calling order, the 15-minute rule, emergencies and the visit link. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class QueueTicketTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    /** Each test runs on a day of its own, so tickets from other tests never get called. */
    private static final AtomicInteger DAY = new AtomicInteger();

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

    private Staff reception;
    private Staff nurse;

    @BeforeEach
    void setUp() {
        // Mid-morning in Nairobi, on a fresh day.
        clock.set(Instant.parse("2031-03-02T07:00:00Z").plus(Duration.ofDays(DAY.incrementAndGet())));
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
    }

    private JsonNode issue(TicketLane lane) throws Exception {
        String body = mvc.perform(post("/api/queue/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lane\":\"" + lane + "\"}")
                        .with(as(reception)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(body);
    }

    private ResultActions callNext() throws Exception {
        return mvc.perform(post("/api/queue/tickets/call-next").with(as(reception)));
    }

    private String calledCode() throws Exception {
        String body =
                callNext().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("code").asString();
    }

    @Test
    void eachLaneNumbersItsTicketsFromOneEachDay() throws Exception {
        assertThat(issue(TicketLane.NEW).get("code").asString()).isEqualTo("N-001");
        assertThat(issue(TicketLane.NEW).get("code").asString()).isEqualTo("N-002");
        assertThat(issue(TicketLane.RETURNING).get("code").asString()).isEqualTo("R-001");
        assertThat(issue(TicketLane.EMERGENCY).get("code").asString()).isEqualTo("E-001");

        clock.advance(Duration.ofDays(1));
        DAY.incrementAndGet();
        assertThat(issue(TicketLane.NEW).get("code").asString()).isEqualTo("N-001");
    }

    @Test
    void preRegisteredAndReturningPatientsAreCalledBeforeNewOnesInArrivalOrder() throws Exception {
        issue(TicketLane.NEW);
        clock.advance(Duration.ofMinutes(1));
        issue(TicketLane.RETURNING);
        clock.advance(Duration.ofMinutes(1));
        issue(TicketLane.PRE_REGISTERED);
        clock.advance(Duration.ofMinutes(1));

        assertThat(calledCode()).isEqualTo("R-001");
        assertThat(calledCode()).isEqualTo("P-001");
        assertThat(calledCode()).isEqualTo("N-001");
        callNext().andExpect(status().isNoContent());
    }

    @Test
    void aNewPatientIsNeverPassedOverOnceTheyHaveWaitedFifteenMinutes() throws Exception {
        issue(TicketLane.NEW);
        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        issue(TicketLane.RETURNING);
        issue(TicketLane.RETURNING);

        // At 14:59 the returning patient still goes first.
        assertThat(calledCode()).isEqualTo("R-001");

        clock.advance(Duration.ofSeconds(1));
        assertThat(calledCode()).isEqualTo("N-001");
        assertThat(calledCode()).isEqualTo("R-002");
    }

    @Test
    void emergenciesSkipReceptionAndGoStraightToClinicalStaff() throws Exception {
        JsonNode emergency = issue(TicketLane.EMERGENCY);
        String id = emergency.get("id").asString();

        callNext().andExpect(status().isNoContent());
        mvc.perform(get("/api/queue/tickets").with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("E-001"))
                .andExpect(jsonPath("$[0].lane").value("EMERGENCY"));

        mvc.perform(post("/api/queue/tickets/" + id + "/attend").with(as(reception)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/queue/tickets/" + id + "/attend").with(as(nurse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"));
        mvc.perform(post("/api/queue/tickets/" + id + "/attend")
                        .with(as(ActingAs.staff(staffRepository, StaffRole.DOCTOR))))
                .andExpect(status().isConflict());
    }

    @Test
    void theWaitingAreaSeesCodesOnlyWithoutLoggingIn() throws Exception {
        issue(TicketLane.NEW);
        issue(TicketLane.RETURNING);
        issue(TicketLane.EMERGENCY);
        calledCode();

        String body = mvc.perform(get("/api/public/queue-board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.called[0]").value("R-001"))
                .andExpect(jsonPath("$.waiting[0]").value("N-001"))
                .andExpect(jsonPath("$.waiting.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("E-001").doesNotContain("id");
    }

    @Test
    void aVisitOpenedForATicketCountsItsWaitFromTheTicket() throws Exception {
        JsonNode ticket = issue(TicketLane.NEW);
        Instant issuedAt = Instant.parse(ticket.get("arrivedAt").asString());
        clock.advance(Duration.ofMinutes(10));
        calledCode();
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Ticket Patient",
                        LocalDate.of(1992, 4, 4),
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
        Map<String, Object> open = new HashMap<>();
        open.put("patientId", patientId);
        open.put("ticketId", ticket.get("id").asString());

        String visit = mvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(open))
                        .with(as(reception)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID visitId = UUID.fromString(JSON.readTree(visit).get("id").asString());

        assertThat(visits.get(visitId).getArrivedAt()).isEqualTo(issuedAt);
        assertThat(tickets.today()).noneMatch(t -> t.getCode().equals("N-001"));
    }

    @Test
    void aTicketThatWasNotCalledCannotOpenAVisit() throws Exception {
        JsonNode ticket = issue(TicketLane.NEW);
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Waiting Patient",
                        LocalDate.of(1992, 4, 4),
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
        Map<String, Object> open = new HashMap<>();
        open.put("patientId", patientId);
        open.put("ticketId", ticket.get("id").asString());

        mvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(open))
                        .with(as(reception)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This ticket is not waiting to be seen"));
    }

    @Test
    void twoDesksCallingAtOnceGetDifferentTickets() throws Exception {
        issue(TicketLane.NEW);
        issue(TicketLane.NEW);
        Staff otherDesk = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<String>> calls = new ArrayList<>();
        for (Staff desk : List.of(reception, otherDesk)) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return tickets.callNext(new QueueTicketService.Actor(desk.getId(), desk.getRole()))
                        .map(QueueTicket::getCode)
                        .orElse("none");
            }));
        }
        start.countDown();

        List<String> codes = calls.stream().map(CompletableFuture::join).toList();
        assertThat(codes).containsExactlyInAnyOrder("N-001", "N-002");
    }

    @Test
    void onlyReceptionIssuesAndCalls() throws Exception {
        mvc.perform(post("/api/queue/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lane\":\"NEW\"}")
                        .with(as(nurse))
                        .with(ApiBrowser.validCsrfToken()))
                .andExpect(status().isForbidden());
    }
}
