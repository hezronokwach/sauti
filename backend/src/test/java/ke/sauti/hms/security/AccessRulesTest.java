package ke.sauti.hms.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ApiBrowser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The "wrong person" table: for every endpoint, who may call it. Without a session every
 * protected endpoint answers 401; every role not listed answers 403; every listed role
 * gets through. Each new endpoint adds its row here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AccessRulesTest {

    private static final Set<StaffRole> ANY_ROLE = EnumSet.allOf(StaffRole.class);
    private static final Set<StaffRole> ADMIN = EnumSet.of(StaffRole.ADMIN);
    private static final Set<StaffRole> CLINICIANS = EnumSet.of(StaffRole.NURSE, StaffRole.DOCTOR);
    private static final Set<StaffRole> ASK_CONSENT =
            EnumSet.of(StaffRole.RECEPTIONIST, StaffRole.NURSE, StaffRole.DOCTOR);
    private static final Set<StaffRole> FIND_PATIENTS =
            EnumSet.of(StaffRole.RECEPTIONIST, StaffRole.NURSE, StaffRole.DOCTOR, StaffRole.CASHIER);

    /**
     * One row per endpoint.
     *
     * @param method the HTTP method
     * @param path the URL
     * @param allowed the roles that may call it
     */
    record Rule(HttpMethod method, String path, Set<StaffRole> allowed) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static final List<Rule> PROTECTED = List.of(
            new Rule(HttpMethod.GET, "/api/auth/me", ANY_ROLE),
            new Rule(HttpMethod.POST, "/api/auth/logout", ANY_ROLE),
            new Rule(HttpMethod.GET, "/api/test/ping", ANY_ROLE),
            new Rule(HttpMethod.GET, "/api/admin/staff", ADMIN),
            new Rule(HttpMethod.POST, "/api/admin/staff", ADMIN),
            new Rule(HttpMethod.POST, "/api/admin/staff/" + UUID.randomUUID() + "/disable", ADMIN),
            new Rule(HttpMethod.GET, "/api/patients?q=achi", FIND_PATIENTS),
            new Rule(HttpMethod.POST, "/api/patients", EnumSet.of(StaffRole.RECEPTIONIST)),
            new Rule(HttpMethod.GET, "/api/patients/" + UUID.randomUUID(), FIND_PATIENTS),
            new Rule(HttpMethod.GET, "/api/patients/" + UUID.randomUUID() + "/allergies", CLINICIANS),
            new Rule(HttpMethod.POST, "/api/patients/" + UUID.randomUUID() + "/allergies", CLINICIANS),
            new Rule(
                    HttpMethod.POST,
                    "/api/patients/" + UUID.randomUUID() + "/allergies/" + UUID.randomUUID() + "/entered-in-error",
                    CLINICIANS),
            new Rule(HttpMethod.POST, "/api/visits", EnumSet.of(StaffRole.RECEPTIONIST)),
            new Rule(HttpMethod.GET, "/api/visits?status=WAITING_TRIAGE", FIND_PATIENTS),
            new Rule(HttpMethod.GET, "/api/visits/" + UUID.randomUUID(), FIND_PATIENTS),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/take", CLINICIANS),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/status", FIND_PATIENTS),
            new Rule(HttpMethod.GET, "/api/visits/" + UUID.randomUUID() + "/reason", FIND_PATIENTS),
            new Rule(HttpMethod.GET, "/api/consent/wording?language=SWAHILI", ASK_CONSENT),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/consents", ASK_CONSENT),
            new Rule(HttpMethod.GET, "/api/visits/" + UUID.randomUUID() + "/consents", ASK_CONSENT),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/captures", ASK_CONSENT),
            new Rule(HttpMethod.POST, "/api/captures/" + UUID.randomUUID() + "/stop", ASK_CONSENT),
            new Rule(HttpMethod.GET, "/api/captures/" + UUID.randomUUID(), ASK_CONSENT),
            new Rule(HttpMethod.PUT, "/api/captures/" + UUID.randomUUID() + "/pieces/0", ASK_CONSENT),
            new Rule(HttpMethod.GET, "/api/captures/" + UUID.randomUUID() + "/segments", ASK_CONSENT),
            new Rule(HttpMethod.GET, "/api/captures/" + UUID.randomUUID() + "/audio?atMs=0", ASK_CONSENT),
            new Rule(HttpMethod.POST, "/api/captures/" + UUID.randomUUID() + "/erase", ASK_CONSENT),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/vitals", EnumSet.of(StaffRole.NURSE)),
            new Rule(HttpMethod.GET, "/api/visits/" + UUID.randomUUID() + "/vitals", CLINICIANS),
            new Rule(HttpMethod.GET, "/api/visits/" + UUID.randomUUID() + "/note", CLINICIANS),
            new Rule(HttpMethod.PUT, "/api/visits/" + UUID.randomUUID() + "/note", EnumSet.of(StaffRole.DOCTOR)),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/note/sign", EnumSet.of(StaffRole.DOCTOR)),
            new Rule(
                    HttpMethod.POST,
                    "/api/visits/" + UUID.randomUUID() + "/note/addenda",
                    EnumSet.of(StaffRole.DOCTOR)),
            new Rule(HttpMethod.GET, "/api/patients/" + UUID.randomUUID() + "/history", EnumSet.of(StaffRole.DOCTOR)),
            new Rule(HttpMethod.POST, "/api/visits/" + UUID.randomUUID() + "/summary", EnumSet.of(StaffRole.DOCTOR)));

    /** Reachable without a session; the request body may still be refused. */
    static final List<Rule> PUBLIC = List.of(
            new Rule(HttpMethod.POST, "/api/auth/login", ANY_ROLE),
            new Rule(HttpMethod.POST, "/api/auth/code", ANY_ROLE),
            new Rule(HttpMethod.GET, "/actuator/health", ANY_ROLE));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    private final Map<StaffRole, StaffPrincipal> people = new EnumMap<>(StaffRole.class);

    /** A real staff row per role, because audit rows (logout) point at the actor. */
    @BeforeEach
    void onePersonPerRole() {
        for (StaffRole role : StaffRole.values()) {
            Staff staff = new Staff(
                    "Test " + role,
                    role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@example.test",
                    null,
                    role,
                    role.requiresLicence() ? "LIC-1" : null,
                    "not-used");
            staff.changePassword("not-used");
            people.put(role, StaffPrincipal.of(staffRepository.save(staff)));
        }
    }

    private static MockHttpServletRequestBuilder call(Rule rule) {
        return request(rule.method(), rule.path()).with(ApiBrowser.validCsrfToken());
    }

    private MockHttpServletRequestBuilder callAs(Rule rule, StaffRole role) {
        StaffPrincipal principal = people.get(role);
        return call(rule)
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority(principal.authority())))));
    }

    static Stream<Rule> protectedEndpoints() {
        return PROTECTED.stream();
    }

    static Stream<Rule> publicEndpoints() {
        return PUBLIC.stream();
    }

    static Stream<Arguments> wrongRoles() {
        return PROTECTED.stream()
                .flatMap(rule -> Arrays.stream(StaffRole.values())
                        .filter(role -> !rule.allowed().contains(role))
                        .map(role -> Arguments.of(rule, role)));
    }

    static Stream<Arguments> rightRoles() {
        return PROTECTED.stream().flatMap(rule -> rule.allowed().stream().map(role -> Arguments.of(rule, role)));
    }

    @ParameterizedTest(name = "{0} without a session")
    @MethodSource("protectedEndpoints")
    void noSessionGets401(Rule rule) throws Exception {
        mvc.perform(call(rule))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("wrongRoles")
    void aRoleNotListedGets403(Rule rule, StaffRole role) throws Exception {
        mvc.perform(callAs(rule, role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** Past the access rules; the request itself may still be refused (an empty body is a 400). */
    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("rightRoles")
    void aListedRoleGetsThrough(Rule rule, StaffRole role) throws Exception {
        int status = mvc.perform(callAs(rule, role)).andReturn().getResponse().getStatus();

        Assertions.assertThat(status).as(rule + " as " + role).isNotIn(401, 403);
    }

    @ParameterizedTest(name = "{0} is public")
    @MethodSource("publicEndpoints")
    void publicEndpointsNeedNoSession(Rule rule) throws Exception {
        int status = mvc.perform(call(rule)).andReturn().getResponse().getStatus();

        Assertions.assertThat(status).as(rule.toString()).isNotIn(401, 403);
    }
}
