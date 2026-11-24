package ke.sauti.hms.registry;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Registry lookups and the background eligibility check, against the fakes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RegistryEligibilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PatientService patients;

    @Autowired
    private VisitService visits;

    @Autowired
    private EligibilityCheckHandler check;

    @Autowired
    private JdbcTemplate jdbc;

    private Staff reception;

    @BeforeEach
    void setUp() {
        reception = ActingAs.staff(staffRepository, StaffRole.RECEPTIONIST);
    }

    private ResultActions lookup(Staff who, String idNumber) throws Exception {
        return mvc.perform(get("/api/registry/lookup")
                .param("idType", "NATIONAL_ID")
                .param("idNumber", idNumber)
                .with(as(who)));
    }

    private UUID register(String nationalId) {
        return patients.register(new RegisterPatientRequest(
                        "Eligibility Patient " + UUID.randomUUID(),
                        LocalDate.of(1985, 3, 14),
                        Sex.FEMALE,
                        null,
                        nationalId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .getId();
    }

    private String eligibility(UUID patientId) throws Exception {
        return mvc.perform(get("/api/patients/" + patientId + "/eligibility").with(as(reception)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void aRegistryHitGivesNameDateOfBirthAndSex() throws Exception {
        lookup(reception, "30000003")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.fullName").value("Wanjiru Njeri Kamau"))
                .andExpect(jsonPath("$.dateOfBirth").value("1978-11-23"))
                .andExpect(jsonPath("$.sex").value("FEMALE"));
    }

    @Test
    void aMissFallsBackToTypingAndADownRegistryIsA503() throws Exception {
        lookup(reception, "12345678")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
        lookup(reception, FakePeople.UNAVAILABLE_ID)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The registry is not answering; enter the details by hand"));
    }

    @Test
    void lookupsAreForReceptionAndAuditedWithoutTheNumber() throws Exception {
        lookup(ActingAs.staff(staffRepository, StaffRole.NURSE), "30000003").andExpect(status().isForbidden());
        lookup(reception, "30000005").andExpect(status().isOk());

        String detail = jdbc.queryForObject(
                "select detail::text from audit_event where action = 'REGISTRY_LOOKUP' and actor_id = ?"
                        + " order by occurred_at desc limit 1",
                String.class,
                reception.getId());
        assertThat(detail).contains("FOUND").doesNotContain("30000005");
    }

    @Test
    void eachFakeEligibilityOutcomeIsStoredForTheBadge() throws Exception {
        // Each test uses its own made-up IDs: national IDs are unique in the database.
        String[][] cases = {{"30000001", "ELIGIBLE"}, {"30000002", "INELIGIBLE"}, {"31234567", "NOT_FOUND"}};
        for (String[] c : cases) {
            UUID patientId = register(c[0]);

            assertThat(eligibility(patientId)).contains("PENDING");
            check.handle(patientId);
            assertThat(eligibility(patientId)).contains(c[1]);
        }
    }

    @Test
    void aServiceThatDoesNotAnswerIsShownAndRetried() throws Exception {
        UUID patientId = register(FakePeople.UNAVAILABLE_ID);

        assertThatThrownBy(() -> check.handle(patientId)).isInstanceOf(IllegalStateException.class);
        assertThat(eligibility(patientId)).contains("UNAVAILABLE");
    }

    @Test
    void noNationalIdMeansNoCheck() throws Exception {
        UUID patientId = register(null);

        assertThat(eligibility(patientId)).contains("NONE");
        assertThat(jdbc.queryForObject(
                        "select count(*) from job where type = 'ELIGIBILITY_CHECK' and payload_id = ?",
                        Long.class,
                        patientId))
                .isZero();
    }

    @Test
    void eligibilityNeverBlocksCare() throws Exception {
        UUID ineligible = register("30000004");
        check.handle(ineligible);
        UUID pending = register("30000006");

        assertThat(visits.open(ineligible, null, new VisitService.Actor(reception.getId(), reception.getRole())))
                .isNotNull();
        assertThat(visits.open(pending, null, new VisitService.Actor(reception.getId(), reception.getRole())))
                .isNotNull();
    }
}
