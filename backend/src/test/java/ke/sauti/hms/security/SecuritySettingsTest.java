package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ApiBrowser;
import ke.sauti.hms.support.FixedClockConfiguration;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Session limits, CSRF token handling, security headers and error bodies. Cookie flags: {@link CookieFlagsTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class SecuritySettingsTest {

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
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(FixedClockConfiguration.NOW);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
    }

    private Staff staffWithAuthenticator() {
        Staff staff = new Staff(
                "Test Person",
                "staff-" + UUID.randomUUID() + "@example.test",
                null,
                StaffRole.NURSE,
                "NCK-1",
                passwordEncoder.encode(PASSWORD));
        staff.startAuthenticatorSetup(SECRET);
        staff.confirmAuthenticator();
        return staffRepository.save(staff);
    }

    private MvcResult logIn(ApiBrowser browser, Staff staff) throws Exception {
        browser.send(json(post("/api/auth/login"), Map.of("email", staff.getEmail(), "password", PASSWORD)))
                .andExpect(status().isOk());
        return browser.send(json(post("/api/auth/code"), Map.of("code", totp.codeFor(SECRET, totp.currentStep()))))
                .andExpect(status().isOk())
                .andReturn();
    }

    // ---- session ------------------------------------------------------------------

    @Test
    void aSessionExpiresAfterFifteenMinutesWithoutUse() throws Exception {
        ApiBrowser browser = new ApiBrowser(mvc);
        logIn(browser, staffWithAuthenticator());
        String sessionId = new String(Base64.getDecoder().decode(browser.session()), StandardCharsets.UTF_8);

        Integer idleSeconds = jdbc.queryForObject(
                "select max_inactive_interval from spring_session where session_id = ?", Integer.class, sessionId);

        assertThat(idleSeconds).isEqualTo(15 * 60);
    }

    @Test
    void aSessionEndsEightHoursAfterLoginHoweverActive() throws Exception {
        ApiBrowser browser = new ApiBrowser(mvc);
        logIn(browser, staffWithAuthenticator());

        clock.advance(Duration.ofHours(8).minusMinutes(1));
        browser.send(get("/api/auth/me")).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(1));
        browser.send(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- CSRF ---------------------------------------------------------------------

    @Test
    void aChangingRequestWithoutTheHeaderIsRefusedEvenWithTheCookie() throws Exception {
        ApiBrowser browser = new ApiBrowser(mvc);
        browser.send(get("/actuator/health"));
        assertThat(browser.cookie(ApiBrowser.CSRF_COOKIE)).isNotNull();

        browser.sendWithoutCsrfHeader(
                        json(post("/api/auth/login"), Map.of("email", "someone@example.test", "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void theCsrfTokenChangesAtLoginAndAtLogout() throws Exception {
        ApiBrowser browser = new ApiBrowser(mvc);
        browser.send(get("/actuator/health"));
        String beforeLogin = browser.cookie(ApiBrowser.CSRF_COOKIE);

        logIn(browser, staffWithAuthenticator());
        String afterLogin = browser.cookie(ApiBrowser.CSRF_COOKIE);
        assertThat(afterLogin).isNotNull().isNotEqualTo(beforeLogin);

        browser.send(get("/api/auth/me")).andExpect(status().isOk());
        browser.send(post("/api/auth/logout")).andExpect(status().isNoContent());
        browser.send(get("/actuator/health"));

        assertThat(browser.cookie(ApiBrowser.CSRF_COOKIE)).isNotNull().isNotEqualTo(afterLogin);
    }

    @Test
    void anAnonymousRequestDoesNotCreateASession() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/health")).andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith(ApiBrowser.SESSION_COOKIE + "="));
    }

    // ---- headers and error bodies ---------------------------------------------------

    @Test
    void everyResponseCarriesTheSecurityHeaders() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", SecurityConfiguration.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("Permissions-Policy", SecurityConfiguration.PERMISSIONS_POLICY))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void notLoggedInIsA401WithAnApiErrorBody() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("You need to log in"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
