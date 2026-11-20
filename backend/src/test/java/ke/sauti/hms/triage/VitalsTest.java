package ke.sauti.hms.triage;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
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
import tools.jackson.databind.json.JsonMapper;

/** Vital signs at triage: plausible values only, by the nurse holding the patient. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class VitalsTest {

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

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private UUID visitWaitingForTriage() {
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Vitals Patient",
                        LocalDate.of(1975, 12, 12),
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
        UUID visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        return visitId;
    }

    private UUID visitInTriage() {
        UUID visitId = visitWaitingForTriage();
        visits.take(visitId, actor(nurse));
        return visitId;
    }

    private static Map<String, Object> normal() {
        Map<String, Object> body = new HashMap<>();
        body.put("temperatureC", 37.8);
        body.put("pulseBpm", 92);
        body.put("respiratoryRate", 18);
        body.put("systolicMmhg", 124);
        body.put("diastolicMmhg", 82);
        body.put("spo2Percent", 97);
        body.put("weightKg", 64.5);
        return body;
    }

    private ResultActions record(Staff who, UUID visitId, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/vitals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .with(as(who)));
    }

    private long vitalsRows(UUID visitId) {
        Long rows = jdbc.queryForObject("select count(*) from vitals where visit_id = ?", Long.class, visitId);
        return rows == null ? 0 : rows;
    }

    @Test
    void recordingVitalsSendsThePatientToTheDoctor() throws Exception {
        UUID visitId = visitInTriage();

        record(nurse, visitId, normal())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temperatureC").value(37.8))
                .andExpect(jsonPath("$.pulseBpm").value(92))
                .andExpect(jsonPath("$.heightCm").doesNotExist());

        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WAITING_DOCTOR);
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where action = 'VITALS_RECORDED' and visit_id = ?",
                        Long.class,
                        visitId))
                .isEqualTo(1);
    }

    @Test
    void impossibleValuesAreRefusedFieldByField() throws Exception {
        UUID visitId = visitInTriage();
        Map<String, Object> body = normal();
        body.put("temperatureC", 47.0);
        body.put("pulseBpm", 400);
        body.put("spo2Percent", 101);
        body.put("heightCm", 20);

        String content = record(nurse, visitId, body)
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> fields = JSON.readTree(content)
                .get("fieldErrors")
                .valueStream()
                .map(node -> node.get("field").asString())
                .toList();
        assertThat(fields).contains("temperatureC", "pulseBpm", "spo2Percent", "heightCm");
        assertThat(vitalsRows(visitId)).isZero();
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.IN_TRIAGE);
    }

    @Test
    void systolicMustBeAboveDiastolic() throws Exception {
        UUID visitId = visitInTriage();
        Map<String, Object> body = normal();
        body.put("systolicMmhg", 80);
        body.put("diastolicMmhg", 90);

        record(nurse, visitId, body).andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyTheNurseHoldingThePatientRecords() throws Exception {
        UUID visitId = visitInTriage();

        record(ActingAs.staff(staffRepository, StaffRole.NURSE), visitId, normal())
                .andExpect(status().isForbidden());

        assertThat(vitalsRows(visitId)).isZero();
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.IN_TRIAGE);
    }

    @Test
    void vitalsAreTakenOnlyInTriage() throws Exception {
        UUID visitId = visitWaitingForTriage();

        record(nurse, visitId, normal())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vitals are taken while the patient is in triage"));
    }

    @Test
    void theDoctorWhoTakesThePatientReadsThemAndTheReadIsLogged() throws Exception {
        UUID visitId = visitInTriage();
        record(nurse, visitId, normal());
        Staff doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        Staff otherDoctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        visits.take(visitId, actor(doctor));

        mvc.perform(get("/api/visits/" + visitId + "/vitals").with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].systolicMmhg").value(124));
        mvc.perform(get("/api/visits/" + visitId + "/vitals").with(as(otherDoctor)))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                        "select count(*) from access_log where actor_id = ? and what = 'VITALS_VIEWED'",
                        Long.class,
                        doctor.getId()))
                .isEqualTo(1);
    }
}
