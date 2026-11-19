package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.FixedClockConfiguration;
import ke.sauti.hms.support.MutableClock;
import ke.sauti.hms.support.MutableClockConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole login flow over real HTTP requests, real sessions in PostgreSQL and a clock
 * the tests move. Each test uses its own staff member, so tests never share state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class LoginFlowTest {

    private static final String PASSWORD = "Habari-yako-2026";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final String GENERIC_FAILURE =
            "Login failed. Check your details, or try again in 15 minutes if you have had several attempts";
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
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(FixedClockConfiguration.NOW);
    }

    /** One browser: keeps the session cookie between requests and sends a CSRF token. */
    private final class Browser {

        private @Nullable Cookie session;

        ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
            if (session != null) {
                request.cookie(session);
            }
            ResultActions result = mvc.perform(request.with(csrf()));
            Cookie updated = result.andReturn().getResponse().getCookie("SESSION");
            if (updated != null) {
                session = updated.getMaxAge() == 0 ? null : updated;
            }
            return result;
        }

        ResultActions login(String email, String password) throws Exception {
            return send(json(post("/api/auth/login"), Map.of("email", email, "password", password)));
        }

        ResultActions code(String code) throws Exception {
            return send(json(post("/api/auth/code"), Map.of("code", code)));
        }

        ResultActions me() throws Exception {
            return send(get("/api/auth/me"));
        }

        String sessionValue() {
            assertThat(session).isNotNull();
            return session.getValue();
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }

    private static String email() {
        return "staff-" + UUID.randomUUID() + "@example.test";
    }

    private Staff newStaff(String email) {
        return staffRepository.save(
                new Staff("Test Person", email, null, StaffRole.RECEPTIONIST, null, passwordEncoder.encode(PASSWORD)));
    }

    /** A staff member who has already set up an authenticator with {@link #SECRET}. */
    private Staff staffWithAuthenticator(String email) {
        Staff staff =
                new Staff("Test Person", email, null, StaffRole.DOCTOR, "KMPDC-1", passwordEncoder.encode(PASSWORD));
        staff.startAuthenticatorSetup(SECRET);
        staff.confirmAuthenticator();
        return staffRepository.save(staff);
    }

    private String currentCode(String secret) {
        return totp.codeFor(secret, totp.currentStep());
    }

    private long auditRows(String action, UUID staffId) {
        Long rows = jdbc.queryForObject(
                "select count(*) from audit_event where action = ? and entity_id = ?", Long.class, action, staffId);
        return rows == null ? 0 : rows;
    }

    private int failedLogins(UUID staffId) {
        Integer failed = jdbc.queryForObject("select failed_logins from staff where id = ?", Integer.class, staffId);
        return failed == null ? -1 : failed;
    }

    private Browser loggedIn(Staff staff) throws Exception {
        Browser browser = new Browser();
        browser.login(staff.getEmail(), PASSWORD).andExpect(status().isOk());
        browser.code(currentCode(SECRET)).andExpect(status().isOk());
        return browser;
    }

    // ---- first login and normal login ---------------------------------------------

    @Test
    void aFirstLoginSetsUpTheAuthenticatorThenLogsIn() throws Exception {
        Staff staff = newStaff(email());
        Browser browser = new Browser();

        MvcResult login = browser.login(staff.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeRequired").value(true))
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andReturn();
        String uri = JSON.readTree(login.getResponse().getContentAsString())
                .get("otpauthUri")
                .asString();
        assertThat(uri).startsWith("otpauth://totp/Sauti%3A").contains("&issuer=Sauti");
        String secret = uri.replaceAll(".*secret=([A-Z2-7]+).*", "$1");
        String sessionBeforeCode = browser.sessionValue();

        browser.code(currentCode(secret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(staff.getId().toString()))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"));

        assertThat(browser.sessionValue()).isNotEqualTo(sessionBeforeCode);
        browser.me().andExpect(status().isOk()).andExpect(jsonPath("$.email").value(staff.getEmail()));
        assertThat(jdbc.queryForObject("select mfa_enabled from staff where id = ?", Boolean.class, staff.getId()))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select actor_id from audit_event where action = 'LOGIN_OK' and entity_id = ?",
                        UUID.class,
                        staff.getId()))
                .isEqualTo(staff.getId());
    }

    @Test
    void aSecondUnfinishedSetupKeepsTheSameSecret() throws Exception {
        Staff staff = newStaff(email());

        String first = JSON.readTree(new Browser()
                        .login(staff.getEmail(), PASSWORD)
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("otpauthUri")
                .asString();
        String second = JSON.readTree(new Browser()
                        .login(staff.getEmail(), PASSWORD)
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("otpauthUri")
                .asString();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void aSetUpAuthenticatorOnlyAsksForTheCode() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = new Browser();

        browser.login(staff.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeRequired").value(true))
                .andExpect(jsonPath("$.setupRequired").value(false))
                .andExpect(jsonPath("$.otpauthUri").doesNotExist());
        browser.code(currentCode(SECRET)).andExpect(status().isOk());

        browser.me().andExpect(status().isOk()).andExpect(jsonPath("$.role").value("DOCTOR"));
        assertThat(auditRows("LOGIN_OK", staff.getId())).isEqualTo(1);
    }

    @Test
    void thePasswordStepAloneDoesNotLogAnyoneIn() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = new Browser();

        browser.login(staff.getEmail(), PASSWORD).andExpect(status().isOk());

        browser.me().andExpect(status().isUnauthorized());
    }

    // ---- refusals -----------------------------------------------------------------

    @Test
    void aWrongPasswordGivesTheGenericFailureAndIsCountedAndAudited() throws Exception {
        Staff staff = staffWithAuthenticator(email());

        new Browser()
                .login(staff.getEmail(), "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

        assertThat(failedLogins(staff.getId())).isEqualTo(1);
        assertThat(auditRows("LOGIN_FAILED", staff.getId())).isEqualTo(1);
    }

    @Test
    void anUnknownEmailGetsTheSameReplyAsAWrongPassword() throws Exception {
        new Browser()
                .login(email(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutes() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = new Browser();
        for (int i = 0; i < Staff.MAX_FAILED_LOGINS; i++) {
            browser.login(staff.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
        }

        browser.login(staff.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GENERIC_FAILURE));

        clock.advance(Duration.ofMinutes(15));
        browser.login(staff.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeRequired").value(true));
    }

    @Test
    void aDisabledAccountCannotLogIn() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        staff.disable();
        staffRepository.save(staff);

        new Browser()
                .login(staff.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void aWrongCodeCountsAsAFailureButTheRightOneStillWorks() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = new Browser();
        browser.login(staff.getEmail(), PASSWORD).andExpect(status().isOk());
        String right = currentCode(SECRET);
        String wrong = right.equals("000000") ? "000001" : "000000";

        browser.code(wrong)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        assertThat(failedLogins(staff.getId())).isEqualTo(1);

        browser.code(right).andExpect(status().isOk());
        assertThat(failedLogins(staff.getId())).isZero();
    }

    @Test
    void aCodeAfterFiveMinutesMeansStartingAgain() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = new Browser();
        browser.login(staff.getEmail(), PASSWORD).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(5));

        browser.code(currentCode(SECRET))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_EXPIRED"));
    }

    @Test
    void aCodeWithoutAPasswordStepIsRefused() throws Exception {
        new Browser()
                .code("123456")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_EXPIRED"));
    }

    @Test
    void aChangingRequestWithoutTheCsrfTokenIsRefused() throws Exception {
        mvc.perform(json(post("/api/auth/login"), Map.of("email", email(), "password", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ---- me and logout ------------------------------------------------------------

    @Test
    void meWithoutASessionIs401() throws Exception {
        new Browser().me().andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSessionAndIsAudited() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = loggedIn(staff);
        Cookie oldSession = new Cookie("SESSION", browser.sessionValue());

        browser.send(post("/api/auth/logout")).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        assertThat(auditRows("LOGOUT", staff.getId())).isEqualTo(1);
    }

    // ---- forced password change ---------------------------------------------------

    @Test
    void aOneTimePasswordAllowsOnlyThePasswordChangeUntilItIsDone() throws Exception {
        Staff staff = staffWithAuthenticator(email());
        Browser browser = loggedIn(staff);

        browser.me().andExpect(jsonPath("$.mustChangePassword").value(true));
        browser.send(get("/api/test/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        browser.send(json(
                        post("/api/auth/password"),
                        Map.of("currentPassword", "not-the-password", "newPassword", "A-much-better-one-2026")))
                .andExpect(status().isUnprocessableContent());
        browser.send(json(post("/api/auth/password"), Map.of("currentPassword", PASSWORD, "newPassword", "too-short")))
                .andExpect(status().isBadRequest());
        browser.send(json(post("/api/auth/password"), Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD)))
                .andExpect(status().isUnprocessableContent());

        browser.send(json(
                        post("/api/auth/password"),
                        Map.of("currentPassword", PASSWORD, "newPassword", "A-much-better-one-2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        browser.send(get("/api/test/ping")).andExpect(status().isOk());
        browser.me().andExpect(jsonPath("$.mustChangePassword").value(false));
        String hash = jdbc.queryForObject("select password_hash from staff where id = ?", String.class, staff.getId());
        assertThat(passwordEncoder.matches("A-much-better-one-2026", hash)).isTrue();
        assertThat(auditRows("PASSWORD_CHANGED", staff.getId())).isEqualTo(1);
    }
}
