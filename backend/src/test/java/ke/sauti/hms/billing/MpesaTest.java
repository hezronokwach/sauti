package ke.sauti.hms.billing;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.billing.mpesa.FakeMpesaGateway;
import ke.sauti.hms.billing.mpesa.MpesaGateway.Outcome;
import ke.sauti.hms.billing.mpesa.MpesaGateway.QueryResult;
import ke.sauti.hms.billing.mpesa.MpesaRejectedException;
import ke.sauti.hms.note.NoteService;
import ke.sauti.hms.note.dto.SaveNoteRequest;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.security.CodeVerifier;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.json.JsonMapper;

/** A payment counts only after M-Pesa confirms it, whatever the callbacks say. */
@SpringBootTest(properties = "sauti.mpesa.callback-secret=" + MpesaTest.SECRET)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class MpesaTest {

    static final String SECRET = "test-callback-secret-with-32-characters";
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String GOOD_CODE = "123456";
    private static final String PHONE = "0712345678";
    // Receipts are unique across the shared test database.
    private static final String RECEIPT =
            "SAB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final BigDecimal BILL = new BigDecimal("500");
    private static final QueryResult CONFIRMED_NO_DETAILS = new QueryResult(Outcome.SUCCESS, null, null);
    private static final QueryResult STILL_WAITING = new QueryResult(Outcome.PROCESSING, null, null);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private NoteService notes;

    @Autowired
    private BillingService billing;

    @Autowired
    private MpesaService mpesa;

    @Autowired
    private FakeMpesaGateway gateway;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private CodeVerifier verifier;

    private Staff cashier;
    private UUID visitId;

    @BeforeEach
    void setUp() {
        Mockito.when(verifier.verify(ArgumentMatchers.any(), ArgumentMatchers.eq(GOOD_CODE)))
                .thenReturn(true);
        Staff reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
        Staff nurse = ActingAs.staff(staffRepository, StaffRole.NURSE);
        Staff doctor = ActingAs.staff(staffRepository, StaffRole.DOCTOR);
        doctor.startAuthenticatorSetup("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
        doctor.confirmAuthenticator();
        doctor = staffRepository.save(doctor);
        cashier = ActingAs.staff(staffRepository, StaffRole.CASHIER);
        UUID patientId = patients.register(new RegisterPatientRequest(
                        "Mpesa Patient",
                        LocalDate.of(1990, 4, 4),
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
        long version = notes.saveDraft(
                        visitId, new SaveNoteRequest(null, "Cough", null, null, "Cold", "Rest", false), actor(doctor))
                .note()
                .getVersion();
        notes.sign(visitId, version, GOOD_CODE, actor(doctor));
        billing.finishConsultation(visitId, actor(doctor));
    }

    @AfterEach
    void resetGateway() {
        gateway.answerWith(null);
        gateway.failPushWith(null);
    }

    private static VisitService.Actor actor(Staff staff) {
        return new VisitService.Actor(staff.getId(), staff.getRole());
    }

    private ResultActions pay(String key) throws Exception {
        return mvc.perform(post("/api/visits/" + visitId + "/invoice/mpesa")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("phone", PHONE, "idempotencyKey", key)))
                .with(as(cashier)));
    }

    private ResultActions resolve(boolean paid, String receipt, String reason) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("paid", paid);
        body.put("receipt", receipt);
        body.put("reason", reason);
        return mvc.perform(post("/api/visits/" + visitId + "/invoice/mpesa/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .with(as(cashier)));
    }

    private void paySuccessfully() throws Exception {
        pay(UUID.randomUUID().toString()).andExpect(status().isOk());
    }

    private UUID paymentId() {
        return jdbc.queryForObject(
                "select p.id from payment p join invoice i on i.id = p.invoice_id where i.visit_id = ?"
                        + " order by p.id desc limit 1",
                UUID.class,
                visitId);
    }

    /** Safaricom's id as the application reads it; the column itself holds it encrypted. */
    private String checkoutId() {
        return paymentRepository.findById(paymentId()).orElseThrow().getCheckoutRequestId();
    }

    private String paymentStatus() {
        return jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId());
    }

    private String invoiceStatus() {
        return jdbc.queryForObject("select status from invoice where visit_id = ?", String.class, visitId);
    }

    private int queuedVerifications() {
        Integer count = jdbc.queryForObject(
                "select count(*) from job where type = ? and payload_id = ? and status = 'QUEUED'",
                Integer.class,
                MpesaService.VERIFY_JOB,
                paymentId());
        return count == null ? 0 : count;
    }

    /** Runs what the worker would: every queued verification of this payment. */
    private void runVerification() {
        UUID id = paymentId();
        List<UUID> queued = jdbc.queryForList(
                "update job set status = 'DONE' where type = ? and payload_id = ? and status = 'QUEUED' returning id",
                UUID.class,
                MpesaService.VERIFY_JOB,
                id);
        queued.forEach(job -> mpesa.verify(id));
    }

    private void callback(BigDecimal amount, String receipt) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "Body",
                Map.of(
                        "stkCallback",
                        Map.of(
                                "CheckoutRequestID",
                                checkoutId(),
                                "ResultCode",
                                0,
                                "CallbackMetadata",
                                Map.of(
                                        "Item",
                                        List.of(
                                                Map.of("Name", "Amount", "Value", amount),
                                                Map.of("Name", "MpesaReceiptNumber", "Value", receipt)))))));
        mvc.perform(post("/api/public/mpesa/callback/" + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value(0));
    }

    @Test
    void aDoubleClickSendsOnePrompt() throws Exception {
        int before = gateway.pushes();
        String key = UUID.randomUUID().toString();
        pay(key).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        pay(key).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        assertThat(gateway.pushes()).isEqualTo(before + 1);

        pay(UUID.randomUUID().toString()).andExpect(status().isConflict());
        assertThat(gateway.pushes()).isEqualTo(before + 1);
    }

    @Test
    void safaricomsIdIsStoredEncrypted() throws Exception {
        paySuccessfully();
        String id = checkoutId();
        assertThat(id).isNotBlank();
        byte[] stored =
                jdbc.queryForObject("select checkout_request_id from payment where id = ?", byte[].class, paymentId());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(id);
        assertThat(jdbc.queryForObject(
                        "select checkout_request_hash is not null from payment where id = ?",
                        Boolean.class,
                        paymentId()))
                .isTrue();
    }

    @Test
    void threeCallbacksGiveOnePaid() throws Exception {
        gateway.answerWith(CONFIRMED_NO_DETAILS);
        paySuccessfully();
        callback(BILL, RECEIPT);
        callback(BILL, RECEIPT);
        callback(BILL, RECEIPT);
        assertThat(queuedVerifications()).isEqualTo(1);

        runVerification();

        assertThat(paymentStatus()).isEqualTo("PAID");
        assertThat(invoiceStatus()).isEqualTo("PAID");
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.DONE);
        assertThat(jdbc.queryForObject("select mpesa_receipt from payment where id = ?", String.class, paymentId()))
                .isEqualTo(RECEIPT);
        runVerification();
        assertThat(paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void aCallbackAloneNeverMarksPaid() throws Exception {
        gateway.answerWith(STILL_WAITING);
        paySuccessfully();
        callback(BILL, RECEIPT);

        runVerification();

        assertThat(paymentStatus()).isEqualTo("PENDING");
        assertThat(invoiceStatus()).isEqualTo("UNPAID");
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WAITING_PAYMENT);
    }

    @Test
    void aMissingCallbackSettlesThroughTheSweep() throws Exception {
        paySuccessfully();
        clock.advance(Duration.ofSeconds(89));
        mpesa.sweep();
        assertThat(queuedVerifications()).isZero();

        clock.advance(Duration.ofSeconds(2));
        mpesa.sweep();
        mpesa.sweep();
        assertThat(queuedVerifications()).isEqualTo(1);
        runVerification();

        assertThat(paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void anAmountMismatchNeverMarksPaid() throws Exception {
        gateway.answerWith(CONFIRMED_NO_DETAILS);
        paySuccessfully();
        callback(new BigDecimal("400"), RECEIPT);

        runVerification();

        assertThat(paymentStatus()).isEqualTo("UNCONFIRMED");
        assertThat(invoiceStatus()).isEqualTo("UNPAID");
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.WAITING_PAYMENT);
    }

    @Test
    void successWithNoAmountEndsWithAPersonChecking() throws Exception {
        gateway.answerWith(CONFIRMED_NO_DETAILS);
        paySuccessfully();
        clock.advance(Duration.ofSeconds(91));
        mpesa.sweep();
        runVerification();
        assertThat(paymentStatus()).isEqualTo("PENDING");

        clock.advance(Duration.ofMinutes(10));
        runVerification();

        assertThat(paymentStatus()).isEqualTo("UNCONFIRMED");
        assertThat(invoiceStatus()).isEqualTo("UNPAID");
    }

    @Test
    void aFailedPromptAllowsAnotherTry() throws Exception {
        gateway.answerWith(new QueryResult(Outcome.FAILED, null, null));
        paySuccessfully();
        callback(BILL, RECEIPT);
        runVerification();
        assertThat(paymentStatus()).isEqualTo("FAILED");
        assertThat(invoiceStatus()).isEqualTo("UNPAID");

        gateway.answerWith(null);
        pay(UUID.randomUUID().toString()).andExpect(status().isOk());
        assertThat(paymentStatus()).isEqualTo("PENDING");
    }

    @Test
    void aRefusedPromptIsFailedButAnUnclearOneIsNot() throws Exception {
        gateway.failPushWith(new MpesaRejectedException("refused"));
        pay(UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"));

        gateway.failPushWith(new IllegalStateException("connection lost"));
        pay(UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("UNCONFIRMED"));
    }

    @Test
    void anOpenMpesaPaymentBlocksCash() throws Exception {
        gateway.answerWith(STILL_WAITING);
        paySuccessfully();

        mvc.perform(post("/api/visits/" + visitId + "/invoice/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(
                                Map.of("idempotencyKey", UUID.randomUUID().toString())))
                        .with(as(cashier)))
                .andExpect(status().isConflict());
        assertThat(invoiceStatus()).isEqualTo("UNPAID");
    }

    @Test
    void theCashierResolvesAnUnconfirmedPaymentWithAReason() throws Exception {
        gateway.failPushWith(new IllegalStateException("connection lost"));
        paySuccessfully();

        resolve(true, null, "Seen on the M-Pesa statement").andExpect(status().isUnprocessableContent());
        resolve(true, RECEIPT, "short").andExpect(status().isBadRequest());
        resolve(true, "RES" + UUID.randomUUID().toString().replace("-", ""), "Seen on the M-Pesa statement")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        assertThat(invoiceStatus()).isEqualTo("PAID");
        assertThat(visits.get(visitId).getStatus()).isEqualTo(VisitStatus.DONE);
        String detail = jdbc.queryForObject(
                "select detail::text from audit_event where action = 'PAYMENT_RESOLVED' and visit_id = ?",
                String.class,
                visitId);
        assertThat(detail).contains("PAID").doesNotContain("statement");
        byte[] stored =
                jdbc.queryForObject("select resolution_reason from payment where id = ?", byte[].class, paymentId());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("statement");
    }

    @Test
    void theWrongSecretIsNotFoundAndChangesNothing() throws Exception {
        paySuccessfully();
        mvc.perform(post("/api/public/mpesa/callback/wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        assertThat(queuedVerifications()).isZero();
    }

    @Test
    void anUnreadableCallbackIsAnsweredAndIgnored() throws Exception {
        paySuccessfully();
        mvc.perform(post("/api/public/mpesa/callback/" + SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isOk());
        assertThat(queuedVerifications()).isZero();
    }
}
