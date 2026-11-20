package ke.sauti.hms.prereg;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.patient.Patient;
import ke.sauti.hms.patient.PatientService;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.ApiBrowser;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The public pre-registration page: saves only, never reveals a record, rate limited, gone at day's end. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class PreRegistrationTest {

    private static final JsonMapper JSON = JsonMapper.shared();
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
    private PreRegistrationExpiryHandler expiry;

    @Autowired
    private JdbcTemplate jdbc;

    private String address;

    @BeforeEach
    void setUp() {
        int day = DAY.incrementAndGet();
        clock.set(Instant.parse("2032-05-01T06:00:00Z").plus(Duration.ofDays(day)));
        address = "10.0." + day + ".1";
    }

    private static Map<String, Object> form(String name, String phone) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("dateOfBirth", "1988-09-15");
        body.put("sex", "FEMALE");
        body.put("phone", phone);
        body.put("reasonForVisit", "Homa na kikohozi");
        return body;
    }

    private ResultActions submit(Map<String, Object> body, String from) throws Exception {
        return mvc.perform(post("/api/public/pre-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .with(ApiBrowser.validCsrfToken())
                .with(request -> {
                    request.setRemoteAddr(from);
                    return request;
                }));
    }

    private static String phone(int n) {
        return String.format(java.util.Locale.ROOT, "0712%06d", n);
    }

    @Test
    void thePublicPageGetsATicketCodeAndNothingElseEvenForAKnownPatient() throws Exception {
        Patient known = patients.register(new RegisterPatientRequest(
                "Wanjiku Known Patient",
                LocalDate.of(1970, 1, 1),
                Sex.FEMALE,
                "0712555111",
                "12345678",
                null,
                "Nyeri",
                "Next Of Kin Name",
                "0712555222",
                null,
                null,
                null));

        // Someone types the known patient's phone, and a different name.
        String body = submit(form("Somebody Else", "0712555111"), address)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketCode").value("P-001"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode answer = JSON.readTree(body);
        assertThat(answer.propertyNames()).containsExactly("ticketCode");
        assertThat(body)
                .doesNotContain("Wanjiku")
                .doesNotContain(ke.sauti.hms.patient.PatientNumbers.format(known.getPatientNumber()))
                .doesNotContain("12345678")
                .doesNotContain("Nyeri")
                .doesNotContain("Kin")
                .doesNotContain(known.getId().toString());
    }

    @Test
    void entriesAreEncryptedAndCannotBeMatchedToPatientRecords() throws Exception {
        submit(form("Achieng Encrypted", "0712555333"), address).andExpect(status().isCreated());
        patients.register(new RegisterPatientRequest(
                "Achieng Encrypted",
                LocalDate.of(1988, 9, 15),
                Sex.FEMALE,
                "0712555333",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true));

        byte[] name = jdbc.queryForObject(
                "select full_name from pre_registration order by created_at desc limit 1", byte[].class);
        assertThat(new String(name, StandardCharsets.ISO_8859_1)).doesNotContain("Achieng");
        Long matches = jdbc.queryForObject(
                "select count(*) from pre_registration r join patient p on p.phone_hash = r.phone_hash", Long.class);
        assertThat(matches).isZero();
    }

    @Test
    void eachPhoneIsLimitedToThreeADay() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit(form("Same Phone", "0712555444"), "10.9.0." + i).andExpect(status().isCreated());
        }

        submit(form("Same Phone", "0712555444"), "10.9.0.9")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void eachAddressIsLimitedToTenAnHour() throws Exception {
        for (int i = 0; i < 10; i++) {
            submit(form("Many People", phone(DAY.get() * 100 + i)), address).andExpect(status().isCreated());
        }

        submit(form("Many People", phone(DAY.get() * 100 + 50)), address).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(61));
        submit(form("Many People", phone(DAY.get() * 100 + 51)), address).andExpect(status().isCreated());
    }

    @Test
    void nonsenseIsRefusedBeforeAnythingIsSaved() throws Exception {
        submit(form("Bad Phone", "12345"), address).andExpect(status().isUnprocessableContent());
        Map<String, Object> future = form("Future Birth", "0712555666");
        future.put("dateOfBirth", "2099-01-01");
        submit(future, address).andExpect(status().isUnprocessableContent());

        Long saved = jdbc.queryForObject(
                "select count(*) from pre_registration where ip_hash is not null and ticket_date = ?",
                Long.class,
                LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Africa/Nairobi")));
        assertThat(saved).isZero();
    }

    @Test
    void receptionOpensItByTheTicketCodeAndTheReadIsAudited() throws Exception {
        submit(form("Desk Patient", "0712555777"), address).andExpect(status().isCreated());

        mvc.perform(get("/api/pre-registrations/p-001")
                        .with(as(ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Desk Patient"))
                .andExpect(jsonPath("$.phone").value("254712555777"))
                .andExpect(jsonPath("$.reasonForVisit").value("Homa na kikohozi"));
        mvc.perform(get("/api/pre-registrations/P-001").with(as(ActingAs.staff(staffRepository, StaffRole.NURSE))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pre-registrations/P-099")
                        .with(as(ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST))))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where action = 'PRE_REGISTRATION_OPENED'"
                                + " and detail ->> 'ticket' = 'P-001' and occurred_at >= ?",
                        Long.class,
                        Timestamp.from(clock.instant().minusSeconds(5))))
                .isEqualTo(1);
    }

    @Test
    void everyEntryIsDeletedAtTheEndOfItsDay() throws Exception {
        submit(form("Gone Tomorrow", "0712555888"), address).andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("select id from pre_registration order by created_at desc limit 1", UUID.class);
        Timestamp runAfter = jdbc.queryForObject(
                "select run_after from job where type = 'PRE_REGISTRATION_EXPIRY' and payload_id = ?",
                Timestamp.class,
                id);
        LocalDate today = LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Africa/Nairobi"));

        assertThat(runAfter.toInstant())
                .isEqualTo(today.plusDays(1)
                        .atStartOfDay(java.time.ZoneId.of("Africa/Nairobi"))
                        .toInstant());

        expiry.handle(id);
        expiry.handle(id);
        assertThat(jdbc.queryForObject("select count(*) from pre_registration where id = ?", Long.class, id))
                .isZero();
    }
}
