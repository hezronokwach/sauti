package ke.sauti.hms.medication;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.AllergyService;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RecordAllergyRequest;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.security.CodeVerifier;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Each safety rule firing and not firing, and what signing requires. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PrescriptionSafetyTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String GOOD_CODE = "123456";
    private static final String REASON = "Tolerated it last year with no reaction";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private AllergyService allergies;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CodeVerifier verifier;

    private Staff reception;
    private Staff nurse;
    private Staff doctor;
    private Staff cashier;
    private UUID patientId;
    private UUID visitId;

    @BeforeEach
    void setUp() {
        Mockito.when(verifier.verify(ArgumentMatchers.any(), ArgumentMatchers.eq(GOOD_CODE)))
                .thenReturn(true);
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        doctor.startAuthenticatorSetup("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
        doctor.confirmAuthenticator();
        doctor = staffRepository.save(doctor);
        cashier = ActingAs.staff(staffRepository, StaffRole.CASHIER);
        patientId = patients.register(new RegisterPatientRequest(
                        "Safety Patient",
                        LocalDate.of(1980, 2, 2),
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
        visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(visitId, actor(doctor));
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private UUID medicine(String code) {
        return jdbc.queryForObject("select id from medicine where code = ?", UUID.class, code);
    }

    private void noKnownAllergies() {
        allergies.record(patientId, new RecordAllergyRequest(true, null, null, null), actor(nurse));
    }

    private void allergy(String substance, String group) {
        allergies.record(patientId, new RecordAllergyRequest(null, substance, group, null), actor(nurse));
    }

    /** A complete line: one tablet or capsule three times a day for five days. */
    private Map<String, Object> line(String code, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("medicineId", medicine(code));
        body.put("doseValue", 1);
        body.put("doseUnit", "TABLET");
        body.put("frequency", "TDS");
        body.put("durationDays", 5);
        body.put("route", "ORAL");
        body.put("quantity", quantity);
        return body;
    }

    private JsonNode add(Map<String, Object> body) throws Exception {
        JsonNode current = current();
        if (current != null) {
            body.put("revision", current.get("revision").asInt());
        }
        return read(mvc.perform(post("/api/visits/" + visitId + "/prescription/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(body))
                        .with(as(doctor)))
                .andExpect(status().isOk()));
    }

    private JsonNode current() throws Exception {
        ResultActions result =
                mvc.perform(get("/api/visits/" + visitId + "/prescription").with(as(doctor)));
        int code = result.andReturn().getResponse().getStatus();
        return code == 404 ? null : read(result);
    }

    private static JsonNode read(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> rules(JsonNode prescription) {
        List<String> found = new ArrayList<>();
        prescription.get("alerts").forEach(alert -> found.add(alert.get("rule").asString()));
        return found;
    }

    private static String alertId(JsonNode prescription, String rule) {
        for (JsonNode alert : prescription.get("alerts")) {
            if (alert.get("rule").asString().equals(rule)) {
                return alert.get("id").asString();
            }
        }
        throw new AssertionError("No alert for " + rule);
    }

    private ResultActions sign(JsonNode prescription, String code) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/prescription/sign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(
                        Map.of("revision", prescription.get("revision").asInt(), "code", code)))
                .with(as(doctor)));
    }

    private ResultActions override(String alertId, String reason, String code) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/prescription/alerts/" + alertId + "/override")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("reason", reason, "code", code)))
                .with(as(doctor)));
    }

    private ResultActions acknowledge(String alertId) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/prescription/alerts/" + alertId + "/acknowledge")
                .with(as(doctor)));
    }

    @Test
    void aCompleteLineWithNoAllergiesRaisesNothingAndSigns() throws Exception {
        noKnownAllergies();
        JsonNode prescription = add(line("PARA-500-TAB", 15));

        assertThat(rules(prescription)).isEmpty();
        sign(prescription, GOOD_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"));
    }

    @Test
    void r1MissingDetailsBlocksAndCannotBeOverridden() throws Exception {
        noKnownAllergies();
        Map<String, Object> body = line("PARA-500-TAB", 15);
        body.remove("frequency");
        JsonNode prescription = add(body);

        assertThat(rules(prescription)).contains("R1");
        override(alertId(prescription, "R1"), REASON, GOOD_CODE).andExpect(status().isUnprocessableContent());
        sign(prescription, GOOD_CODE).andExpect(status().isUnprocessableContent());
    }

    @Test
    void r2AllergyToTheMedicineBlocks() throws Exception {
        allergy("Paracetamol (rash)", null);
        assertThat(rules(add(line("PARA-500-TAB", 15)))).containsExactly("R2");
    }

    @Test
    void r2DoesNotFireForAnUnrelatedAllergy() throws Exception {
        allergy("Metformin", null);
        assertThat(rules(add(line("PARA-500-TAB", 15)))).isEmpty();
    }

    @Test
    void amoxicillinWithAPenicillinAllergyBlocksSigning() throws Exception {
        allergy("Penicillin", null);
        JsonNode prescription = add(line("AMOX-500-CAP", 15));

        assertThat(rules(prescription)).containsExactly("R3");
        sign(prescription, GOOD_CODE).andExpect(status().isUnprocessableContent());
    }

    @Test
    void r3AlsoFiresFromAnotherMedicineInTheGroupAndFromTheGroupField() throws Exception {
        allergy("Flucloxacillin", null);
        allergy("Some brand", "Penicillins");
        assertThat(rules(add(line("AMOX-500-CAP", 15)))).contains("R3").doesNotContain("R2");
    }

    @Test
    void aspirinIsCaughtForAnIbuprofenAllergy() throws Exception {
        allergy("Ibuprofen", null);
        assertThat(rules(add(line("ASPIR-75-TAB", 15)))).containsExactly("R3");
    }

    @Test
    void r4FlagsTheSameMedicineTwice() throws Exception {
        noKnownAllergies();
        add(line("PARA-500-TAB", 15));
        assertThat(rules(add(line("PARA-120-SYR", 15)))).contains("R4").doesNotContain("R5");
    }

    @Test
    void r5FlagsTwoFromOneGroupButNotDifferentGroups() throws Exception {
        noKnownAllergies();
        assertThat(rules(add(line("IBU-400-TAB", 15)))).isEmpty();
        assertThat(rules(add(line("PARA-500-TAB", 15)))).isEmpty();
        assertThat(rules(add(line("DICLO-50-TAB", 15)))).containsExactly("R5");
    }

    @Test
    void r6FlagsAQuantityThatDoesNotCoverTheCourse() throws Exception {
        noKnownAllergies();
        assertThat(rules(add(line("PARA-500-TAB", 14)))).containsExactly("R6");
    }

    @Test
    void r6WorksFromMilligramsAndDoesNotFireWhenCovered() throws Exception {
        noKnownAllergies();
        Map<String, Object> body = line("PARA-500-TAB", 30);
        body.put("doseValue", 1000);
        body.put("doseUnit", "MG");
        assertThat(rules(add(body))).isEmpty();
    }

    @Test
    void r7FlagsAllergiesNeverAskedUntilAcknowledged() throws Exception {
        JsonNode prescription = add(line("PARA-500-TAB", 15));

        assertThat(rules(prescription)).containsExactly("R7");
        sign(prescription, GOOD_CODE).andExpect(status().isUnprocessableContent());
        acknowledge(alertId(prescription, "R7")).andExpect(status().isOk());
        sign(prescription, GOOD_CODE).andExpect(status().isOk());
    }

    @Test
    void r8FlagsAMisspelledAllergyForAHandCheck() throws Exception {
        allergy("Penicilin", null);
        assertThat(rules(add(line("AMOX-500-CAP", 15)))).containsExactly("R8");
    }

    @Test
    void anOverrideNeedsAReasonAndACodeAndIsAuditedWithoutTheReason() throws Exception {
        allergy("Penicillin", null);
        JsonNode prescription = add(line("AMOX-500-CAP", 15));
        String alertId = alertId(prescription, "R3");

        override(alertId, "too short", GOOD_CODE).andExpect(status().isBadRequest());
        override(alertId, "          spaces  ", GOOD_CODE).andExpect(status().isUnprocessableContent());
        override(alertId, REASON, "000000").andExpect(status().isForbidden());
        override(alertId, REASON, GOOD_CODE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].status").value("OVERRIDDEN"));

        sign(prescription, GOOD_CODE).andExpect(status().isOk());
        String details = jdbc.queryForObject(
                "select detail::text from audit_event where action = 'SAFETY_ALERT_OVERRIDDEN' and entity_id = ?",
                String.class,
                UUID.fromString(alertId));
        assertThat(details).contains("R3").doesNotContain("Tolerated");
    }

    @Test
    void aBlockingAlertCannotBeAcknowledgedAndAReviewAlertCannotBeOverridden() throws Exception {
        allergy("Penicillin", null);
        JsonNode blocked = add(line("AMOX-500-CAP", 14));

        acknowledge(alertId(blocked, "R3")).andExpect(status().isUnprocessableContent());
        override(alertId(blocked, "R6"), REASON, GOOD_CODE).andExpect(status().isUnprocessableContent());
    }

    @Test
    void anAllergyAddedAfterAnEditStopsTheSignature() throws Exception {
        noKnownAllergies();
        JsonNode prescription = add(line("AMOX-500-CAP", 15));
        assertThat(rules(prescription)).isEmpty();

        allergy("Amoxicillin", null);

        sign(prescription, GOOD_CODE).andExpect(status().isUnprocessableContent());
        assertThat(rules(current())).containsExactly("R2");
    }

    @Test
    void aWrongCodeDoesNotSign() throws Exception {
        noKnownAllergies();
        JsonNode prescription = add(line("PARA-500-TAB", 15));

        sign(prescription, "000000").andExpect(status().isForbidden());
        assertThat(current().get("status").asString()).isEqualTo("DRAFT");
    }

    @Test
    void aSignedPrescriptionNeverChangesButCanBeCancelledAndRewritten() throws Exception {
        noKnownAllergies();
        JsonNode prescription = add(line("PARA-500-TAB", 15));
        sign(prescription, GOOD_CODE).andExpect(status().isOk());

        Map<String, Object> more = line("IBU-400-TAB", 15);
        more.put("revision", prescription.get("revision").asInt());
        mvc.perform(post("/api/visits/" + visitId + "/prescription/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(more))
                        .with(as(doctor)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/visits/" + visitId + "/prescription/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                Map.of("revision", prescription.get("revision").asInt(), "code", GOOD_CODE)))
                        .with(as(doctor)))
                .andExpect(status().isNoContent());

        assertThat(current()).isNull();
        assertThat(add(line("IBU-400-TAB", 15)).get("status").asString()).isEqualTo("DRAFT");
    }

    @Test
    void onlyASignedPrescriptionIsSpokenInThePatientsLanguage() throws Exception {
        noKnownAllergies();
        JsonNode prescription = add(line("PARA-500-TAB", 15));
        String instructions = "/api/visits/" + visitId + "/prescription/instructions";
        mvc.perform(get(instructions).with(as(doctor))).andExpect(status().isUnprocessableContent());

        sign(prescription, GOOD_CODE).andExpect(status().isOk());

        mvc.perform(get(instructions).with(as(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("SWAHILI"))
                .andExpect(jsonPath("$.serverAudio").value(false))
                .andExpect(jsonPath("$.lines[0].text")
                        .value("Meza kidonge moja ya Paracetamol, mara tatu kwa siku, kwa siku tano."));
        mvc.perform(get(instructions).param("language", "ENGLISH").with(as(doctor)))
                .andExpect(jsonPath("$.lines[0].text")
                        .value("Take one tablet of Paracetamol, three times a day, for five days."));
        String item = prescription.get("items").get(0).get("id").asString();
        mvc.perform(get(instructions + "/" + item + "/audio").with(as(doctor)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void aCashierSeesThePrescriptionButNotAlertsQuotingAllergies() throws Exception {
        allergy("Penicillin", null);
        add(line("AMOX-500-CAP", 15));
        visits.changeStatus(visitId, VisitStatus.WAITING_PAYMENT, null, actor(doctor));
        // Cashiers are not assigned through a queue yet (issue 44), so the test assigns one.
        jdbc.update(
                "insert into care_assignment (visit_id, staff_id, role, started_at) values (?, ?, 'CASHIER', now())",
                visitId,
                cashier.getId());

        mvc.perform(get("/api/visits/" + visitId + "/prescription").with(as(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.alerts.length()").value(0));
    }

    @Test
    void alertTextIsEncryptedInTheDatabase() throws Exception {
        allergy("Penicillin", null);
        add(line("AMOX-500-CAP", 15));

        byte[] stored = jdbc.queryForObject(
                "select a.message from safety_alert a join prescription p on p.id = a.prescription_id"
                        + " where p.visit_id = ?",
                byte[].class,
                visitId);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Penicillin");
    }
}
