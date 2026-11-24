package ke.sauti.hms.visit;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A visit's whole journey and every refusal on the way, over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class VisitApiTest {

    private static final JsonMapper JSON = JsonMapper.shared();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;
    private Staff cashier;

    @BeforeEach
    void people() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        cashier = ActingAs.staff(staffRepository, StaffRole.CASHIER);
    }

    private UUID newPatient() {
        return patients.register(new RegisterPatientRequest(
                        "Visit Patient " + UUID.randomUUID().toString().substring(0, 6),
                        LocalDate.of(1985, 3, 2),
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
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String open(UUID patientId) throws Exception {
        return body(mvc.perform(json(
                                        post("/api/visits"),
                                        Map.of("patientId", patientId, "reasonForVisit", "Homa na kikohozi"))
                                .with(as(reception)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("AT_RECEPTION")))
                .get("id")
                .asString();
    }

    private ResultActions move(Staff who, String visitId, String to) throws Exception {
        return move(who, visitId, to, null);
    }

    private ResultActions move(Staff who, String visitId, String to, String reason) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("to", to);
        if (reason != null) {
            request.put("reason", reason);
        }
        return mvc.perform(
                json(post("/api/visits/" + visitId + "/status"), request).with(as(who)));
    }

    private ResultActions take(Staff who, String visitId) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/take").with(as(who)));
    }

    private List<String> queue(String status) throws Exception {
        return body(mvc.perform(get("/api/visits").param("status", status).with(as(nurse)))
                        .andExpect(status().isOk()))
                .valueStream()
                .map(node -> node.get("visitId").asString())
                .toList();
    }

    @Test
    void aVisitGoesFromReceptionToDone() throws Exception {
        UUID patientId = newPatient();
        String id = open(patientId);

        move(reception, id, "WAITING_TRIAGE").andExpect(status().isOk());
        assertThat(queue("WAITING_TRIAGE")).contains(id);
        take(nurse, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRIAGE"));
        assertThat(queue("WAITING_TRIAGE")).doesNotContain(id);
        // Triage cannot end until someone has asked about allergies.
        move(nurse, id, "WAITING_DOCTOR")
                .andExpect(status().isUnprocessableContent())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Record the patient's allergies, or no known allergies, before sending them to the doctor"));
        mvc.perform(json(post("/api/patients/" + patientId + "/allergies"), Map.of("noKnownAllergies", true))
                        .with(as(nurse)))
                .andExpect(status().is2xxSuccessful());
        move(nurse, id, "WAITING_DOCTOR").andExpect(status().isOk());
        take(doctor, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITH_DOCTOR"))
                .andExpect(jsonPath("$.assignedDoctorId").value(doctor.getId().toString()));
        UUID visitId = UUID.fromString(id);
        // Payment moves go through billing, so the screen endpoint cannot make them.
        move(doctor, id, "WAITING_PAYMENT").andExpect(status().isUnprocessableContent());
        visits.changeStatus(
                visitId, VisitStatus.WAITING_PAYMENT, null, new VisitService.Actor(doctor.getId(), doctor.getRole()));
        move(cashier, id, "DONE").andExpect(status().isUnprocessableContent());
        visits.changeStatus(
                visitId, VisitStatus.DONE, null, new VisitService.Actor(cashier.getId(), cashier.getRole()));

        assertThat(visits.history(visitId))
                .extracting(VisitStatusChange::getToStatus)
                .containsExactly(
                        VisitStatus.AT_RECEPTION,
                        VisitStatus.WAITING_TRIAGE,
                        VisitStatus.IN_TRIAGE,
                        VisitStatus.WAITING_DOCTOR,
                        VisitStatus.WITH_DOCTOR,
                        VisitStatus.WAITING_PAYMENT,
                        VisitStatus.DONE);
        assertThat(visits.assignments(visitId))
                .extracting(CareAssignment::getRole)
                .containsExactly(StaffRole.RECEPTIONIST, StaffRole.NURSE, StaffRole.DOCTOR);
        assertThat(visits.assignments(visitId)).noneMatch(CareAssignment::isOpen);
        assertThat(visits.get(visitId).getClosedAt()).isNotNull();
    }

    @Test
    void theQueueShowsWhoAndSinceWhenButNothingClinical() throws Exception {
        String id = open(newPatient());
        move(reception, id, "WAITING_TRIAGE");

        String content = mvc.perform(
                        get("/api/visits").param("status", "WAITING_TRIAGE").with(as(doctor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(content)
                .contains(id)
                .contains("SH-")
                .contains("waitingSince")
                .doesNotContain("Homa");
    }

    @Test
    void theReasonForTheVisitIsUnreadableInTheTable() throws Exception {
        String id = open(newPatient());

        byte[] raw = jdbc.queryForObject(
                "select reason_for_visit from visit where id = ?", byte[].class, UUID.fromString(id));

        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("Homa");
    }

    @Test
    void queueNumbersFollowOnThroughTheDay() throws Exception {
        String first = open(newPatient());
        String second = open(newPatient());

        int a = visits.get(UUID.fromString(first)).getQueueNumber();
        int b = visits.get(UUID.fromString(second)).getQueueNumber();

        assertThat(b).isGreaterThan(a);
    }

    @Test
    void aPatientHasOneVisitInProgress() throws Exception {
        UUID patient = newPatient();
        open(patient);

        mvc.perform(json(post("/api/visits"), Map.of("patientId", patient)).with(as(reception)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This patient already has a visit in progress"));
    }

    @Test
    void aMoveTheWorkflowDoesNotHaveIs409() throws Exception {
        String id = open(newPatient());

        move(reception, id, "DONE").andExpect(status().isUnprocessableContent());
        move(reception, id, "WITH_DOCTOR").andExpect(status().isConflict());
    }

    @Test
    void theWrongRoleForAMoveIs403() throws Exception {
        String id = open(newPatient());

        move(nurse, id, "WAITING_TRIAGE").andExpect(status().isForbidden());
    }

    @Test
    void onlyTheNurseHoldingThePatientCanMoveThemOn() throws Exception {
        String id = open(newPatient());
        move(reception, id, "WAITING_TRIAGE");
        take(nurse, id);
        Staff otherNurse = ActingAs.staff(staffRepository, StaffRole.NURSE);

        move(otherNurse, id, "WAITING_DOCTOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the person looking after this patient can move them on"));
    }

    @Test
    void takingAPatientSomeoneElseTookIs409() throws Exception {
        String id = open(newPatient());
        move(reception, id, "WAITING_TRIAGE");
        take(nurse, id).andExpect(status().isOk());

        take(ActingAs.staff(staffRepository, StaffRole.NURSE), id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This patient has already been taken or is no longer waiting"));
    }

    @Test
    void cancellingNeedsAReason() throws Exception {
        String id = open(newPatient());

        move(reception, id, "CANCELLED").andExpect(status().isUnprocessableContent());
        move(reception, id, "CANCELLED", "Patient left before triage")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(visits.history(UUID.fromString(id)).getLast().getReason()).isEqualTo("Patient left before triage");
    }

    @Test
    void unknownVisitsAre404() throws Exception {
        take(nurse, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        mvc.perform(get("/api/visits/" + UUID.randomUUID()).with(as(nurse))).andExpect(status().isNotFound());
    }
}
