package ke.sauti.hms.billing;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.capture.CaptureService;
import ke.sauti.hms.capture.ConsentDecision;
import ke.sauti.hms.capture.ConsentGivenBy;
import ke.sauti.hms.capture.ConsentPurpose;
import ke.sauti.hms.capture.ConsentService;
import ke.sauti.hms.note.NoteService;
import ke.sauti.hms.note.dto.SaveNoteRequest;
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

/** Finishing a consultation, the bill it builds, and settling it exactly once. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BillingTest {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String GOOD_CODE = "123456";

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
    private NoteService notes;

    @Autowired
    private ConsentService consents;

    @Autowired
    private CaptureService captures;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CodeVerifier verifier;

    private Staff nurse;
    private Staff doctor;
    private Staff cashier;
    private UUID visitId;

    @BeforeEach
    void setUp() {
        Mockito.when(verifier.verify(ArgumentMatchers.any(), ArgumentMatchers.eq(GOOD_CODE)))
                .thenReturn(true);
        Staff reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        doctor.startAuthenticatorSetup("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
        doctor.confirmAuthenticator();
        doctor = staffRepository.save(doctor);
        cashier = ActingAs.staff(staffRepository, StaffRole.CASHIER);
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Billing Patient",
                        LocalDate.of(1985, 3, 3),
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
        visitId = visits.open(patientId, null, actor(reception)).getId();
        visits.changeStatus(visitId, VisitStatus.WAITING_TRIAGE, null, actor(reception));
        visits.take(visitId, actor(nurse));
        visits.changeStatus(visitId, VisitStatus.WAITING_DOCTOR, null, actor(nurse));
        visits.take(visitId, actor(doctor));
        allergies.record(patientId, new RecordAllergyRequest(true, null, null, null), actor(nurse));
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private void signNote() {
        long version = notes.saveDraft(
                        visitId,
                        new SaveNoteRequest(null, "Fever", null, null, "Malaria suspected", "Treat", false),
                        actor(doctor))
                .note()
                .getVersion();
        notes.sign(visitId, version, GOOD_CODE, actor(doctor));
    }

    private JsonNode addParacetamol(int quantity) throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("medicineId", jdbc.queryForObject("select id from medicine where code = 'PARA-500-TAB'", UUID.class));
        line.put("doseValue", 1);
        line.put("doseUnit", "TABLET");
        line.put("frequency", "TDS");
        line.put("durationDays", 5);
        line.put("route", "ORAL");
        line.put("quantity", quantity);
        String body = mvc.perform(post("/api/visits/" + visitId + "/prescription/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(line))
                        .with(as(doctor)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(body);
    }

    private void signPrescription(JsonNode prescription) throws Exception {
        mvc.perform(post("/api/visits/" + visitId + "/prescription/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                Map.of("revision", prescription.get("revision").asInt(), "code", GOOD_CODE)))
                        .with(as(doctor)))
                .andExpect(status().isOk());
    }

    private ResultActions finish() throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/finish").with(as(doctor)));
    }

    private ResultActions pay(String method, String key) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/invoice/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("idempotencyKey", key)))
                .with(as(cashier)));
    }

    private void finishWithMedicine() throws Exception {
        signNote();
        signPrescription(addParacetamol(15));
        finish().andExpect(status().isOk());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private int paymentCount() {
        Integer count = jdbc.queryForObject(
                "select count(*) from payment p join invoice i on i.id = p.invoice_id where i.visit_id = ?",
                Integer.class,
                visitId);
        return count == null ? 0 : count;
    }

    @Test
    void finishingBuildsTheBillFromTheFeeAndListPrices() throws Exception {
        signNote();
        signPrescription(addParacetamol(15));
        var price = jdbc.queryForObject(
                "select price_kes from medicine where code = 'PARA-500-TAB'", java.math.BigDecimal.class);

        finish().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].kind").value("CONSULTATION"))
                .andExpect(jsonPath("$.lines[1].kind").value("MEDICINE"))
                .andExpect(jsonPath("$.lines[1].quantity").value(15))
                .andExpect(jsonPath("$.totalKes")
                        .value(new java.math.BigDecimal("500")
                                .add(price.multiply(java.math.BigDecimal.valueOf(15)))
                                .doubleValue()));
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WAITING_PAYMENT);
    }

    @Test
    void aConsultationWithoutMedicineBillsOnlyTheFee() throws Exception {
        signNote();
        finish().andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    void anUnsignedNoteOrPrescriptionStopsFinishing() throws Exception {
        finish().andExpect(status().isUnprocessableContent());
        signNote();
        JsonNode draft = addParacetamol(15);
        finish().andExpect(status().isUnprocessableContent());
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WITH_DOCTOR);
        signPrescription(draft);
        finish().andExpect(status().isOk());
        finish().andExpect(status().isConflict());
    }

    @Test
    void aRunningRecordingStopsFinishing() throws Exception {
        signNote();
        consents.record(
                visitId,
                ConsentPurpose.RECORD_CONSULTATION,
                ConsentDecision.AGREED,
                ConsentGivenBy.PATIENT,
                actor(doctor));
        UUID session = captures.start(visitId, ConsentPurpose.RECORD_CONSULTATION, "sw", actor(doctor))
                .getId();

        finish().andExpect(status().isUnprocessableContent());
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WITH_DOCTOR);

        captures.stop(session, 0, actor(doctor));
        finish().andExpect(status().isOk());
    }

    @Test
    void theStatusEndpointCannotSkipTheBill() throws Exception {
        signNote();
        mvc.perform(post("/api/visits/" + visitId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"WAITING_PAYMENT\"}")
                        .with(as(doctor)))
                .andExpect(status().isUnprocessableContent());
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WITH_DOCTOR);
    }

    @Test
    void cashPaysTheBillAndEndsTheVisit() throws Exception {
        finishWithMedicine();
        pay("cash", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"));
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.DONE);
    }

    @Test
    void insuranceStaysPendingAndClosesTheVisit() throws Exception {
        finishWithMedicine();
        pay("insurance", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSURANCE_PENDING"));
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.DONE);
    }

    @Test
    void aDoubleClickRecordsOnePayment() throws Exception {
        finishWithMedicine();
        String key = key();
        pay("cash", key).andExpect(status().isOk());
        pay("cash", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void aSettledBillCannotBePaidAgain() throws Exception {
        finishWithMedicine();
        pay("cash", key()).andExpect(status().isOk());
        pay("insurance", key()).andExpect(status().isConflict());
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void theCashierSeesChargesButNothingClinical() throws Exception {
        finishWithMedicine();
        String body = mvc.perform(get("/api/visits/" + visitId + "/invoice").with(as(cashier)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("Paracetamol");
        assertThat(body).doesNotContain("TDS", "doseValue", "frequency", "route", "instructions", "Malaria", "allerg");
        mvc.perform(get("/api/visits/" + visitId + "/note").with(as(cashier))).andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/" + visitId + "/vitals").with(as(cashier))).andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/" + visitId + "/reason").with(as(cashier))).andExpect(status().isForbidden());
    }

    @Test
    void readingABillIsAccessLoggedAndItsMedicineIsEncrypted() throws Exception {
        finishWithMedicine();
        mvc.perform(get("/api/visits/" + visitId + "/invoice").with(as(cashier)))
                .andExpect(status().isOk());

        Integer logged = jdbc.queryForObject(
                "select count(*) from access_log where visit_id = ? and what = 'INVOICE_OPENED'",
                Integer.class,
                visitId);
        assertThat(logged).isEqualTo(1);
        byte[] stored = jdbc.queryForObject(
                "select l.description from invoice_line l join invoice i on i.id = l.invoice_id"
                        + " where i.visit_id = ? and l.kind = 'MEDICINE'",
                byte[].class,
                visitId);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("Paracetamol");
    }
}
