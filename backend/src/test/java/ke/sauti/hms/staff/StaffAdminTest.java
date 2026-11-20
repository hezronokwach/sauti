package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.security.StaffPrincipal;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.support.ApiBrowser;
import ke.sauti.hms.support.MutableClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creating, listing and disabling staff accounts, and what each does to logging in. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class StaffAdminTest {

    private static final String PASSWORD = "Habari-yako-2026";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final JsonMapper JSON = JsonMapper.shared();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpGenerator totp;

    @Autowired
    private JdbcTemplate jdbc;

    private static String email(String who) {
        return who + "-" + UUID.randomUUID() + "@example.test";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }

    private Staff saved(StaffRole role, String email) {
        Staff staff = new Staff(
                "Person " + role,
                email,
                null,
                role,
                role.requiresLicence() ? "LIC-1" : null,
                passwordEncoder.encode(PASSWORD));
        staff.startAuthenticatorSetup(SECRET);
        staff.confirmAuthenticator();
        staff.changePassword(passwordEncoder.encode(PASSWORD));
        return staffRepository.save(staff);
    }

    /** Sends a request as this staff member without a full login, for admin calls. */
    private ResultActions as(Staff staff, MockHttpServletRequestBuilder request) throws Exception {
        StaffPrincipal principal = StaffPrincipal.of(staff);
        return mvc.perform(request.with(ApiBrowser.validCsrfToken())
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority(principal.authority()))))));
    }

    private static Map<String, Object> newStaff(String email, StaffRole role, String licence) {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Achieng Odhiambo");
        body.put("email", email);
        body.put("role", role.name());
        if (licence != null) {
            body.put("licenceNumber", licence);
        }
        return body;
    }

    private long auditRows(String action, UUID entityId) {
        Long rows = jdbc.queryForObject(
                "select count(*) from audit_event where action = ? and entity_id = ?", Long.class, action, entityId);
        return rows == null ? 0 : rows;
    }

    // ---- create -------------------------------------------------------------------

    @Test
    void aNewAccountLogsInWithItsOneTimePasswordAndMustChangeIt() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));
        String email = email("nurse");

        MvcResult created = as(admin, json(post("/api/admin/staff"), newStaff(email, StaffRole.NURSE, "NCK-2026")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staff.role").value("NURSE"))
                .andExpect(jsonPath("$.staff.status").value("ACTIVE"))
                .andExpect(jsonPath("$.staff.mustChangePassword").value(true))
                .andExpect(jsonPath("$.staff.authenticatorSetUp").value(false))
                .andReturn();
        JsonNode body = JSON.readTree(created.getResponse().getContentAsString());
        String oneTimePassword = body.get("oneTimePassword").asString();
        UUID id = UUID.fromString(body.get("staff").get("id").asString());
        assertThat(oneTimePassword).hasSize(16);

        ApiBrowser browser = new ApiBrowser(mvc);
        MvcResult login = browser.send(
                        json(post("/api/auth/login"), Map.of("email", email, "password", oneTimePassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andReturn();
        String uri = JSON.readTree(login.getResponse().getContentAsString())
                .get("otpauthUri")
                .asString();
        String secret = uri.replaceAll(".*secret=([A-Z2-7]+).*", "$1");
        browser.send(json(post("/api/auth/code"), Map.of("code", totp.codeFor(secret, totp.currentStep()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        browser.send(get("/api/test/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        assertThat(jdbc.queryForObject(
                        "select actor_id from audit_event where action = 'STAFF_CREATED' and entity_id = ?",
                        UUID.class,
                        id))
                .isEqualTo(admin.getId());
    }

    @Test
    void aDuplicateEmailIsAConflictRegardlessOfCase() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));
        String email = email("cashier");
        saved(StaffRole.CASHIER, email);

        as(admin, json(post("/api/admin/staff"), newStaff(email.toUpperCase(Locale.ROOT), StaffRole.CASHIER, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A staff member with this email already exists"));
    }

    @Test
    void aDoctorNeedsALicenceNumber() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));

        as(admin, json(post("/api/admin/staff"), newStaff(email("doctor"), StaffRole.DOCTOR, "  ")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("A licence number is required for doctors and nurses"));
    }

    @Test
    void anInvalidEmailIsRefused() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));

        as(admin, json(post("/api/admin/staff"), newStaff("not-an-email", StaffRole.CASHIER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    // ---- list ---------------------------------------------------------------------

    @Test
    void theListIsSortedByNameAndHoldsNoSecrets() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));

        MvcResult result =
                as(admin, get("/api/admin/staff")).andExpect(status().isOk()).andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).doesNotContain("passwordHash", "mfaSecret", SECRET, "argon2");
        List<String> names = JSON.readTree(content)
                .valueStream()
                .map(node -> node.get("fullName").asString())
                .toList();
        assertThat(names).isSorted();
    }

    // ---- disable ------------------------------------------------------------------

    @Test
    void disablingEndsThePersonsSessionOnTheNextRequest() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));
        Staff nurse = saved(StaffRole.NURSE, email("nurse"));
        ApiBrowser nursesBrowser = new ApiBrowser(mvc);
        nursesBrowser.send(json(post("/api/auth/login"), Map.of("email", nurse.getEmail(), "password", PASSWORD)));
        nursesBrowser.send(json(post("/api/auth/code"), Map.of("code", totp.codeFor(SECRET, totp.currentStep()))));
        nursesBrowser.send(get("/api/auth/me")).andExpect(status().isOk());

        as(admin, post("/api/admin/staff/" + nurse.getId() + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        nursesBrowser
                .send(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        new ApiBrowser(mvc)
                .send(json(post("/api/auth/login"), Map.of("email", nurse.getEmail(), "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        assertThat(auditRows("STAFF_DISABLED", nurse.getId())).isEqualTo(1);
    }

    @Test
    void disablingTwiceChangesNothingTheSecondTime() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));
        Staff cashier = saved(StaffRole.CASHIER, email("cashier"));

        as(admin, post("/api/admin/staff/" + cashier.getId() + "/disable")).andExpect(status().isOk());
        as(admin, post("/api/admin/staff/" + cashier.getId() + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(auditRows("STAFF_DISABLED", cashier.getId())).isEqualTo(1);
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));

        as(admin, post("/api/admin/staff/" + admin.getId() + "/disable"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
    }

    @Test
    void disablingAnUnknownAccountIs404() throws Exception {
        Staff admin = saved(StaffRole.ADMIN, email("admin"));

        as(admin, post("/api/admin/staff/" + UUID.randomUUID() + "/disable")).andExpect(status().isNotFound());
    }

    // ---- refusals are audited -----------------------------------------------------

    @Test
    void aNurseTurnedAwayFromAdminPagesIsAudited() throws Exception {
        Staff nurse = saved(StaffRole.NURSE, email("nurse"));

        as(nurse, get("/api/admin/staff")).andExpect(status().isForbidden());

        Map<String, Object> row = jdbc.queryForMap(
                "select detail::text as detail from audit_event where action = 'ACCESS_DENIED' and actor_id = ?",
                nurse.getId());
        assertThat(row.get("detail").toString()).contains("/api/admin/staff").contains("GET");
    }
}
