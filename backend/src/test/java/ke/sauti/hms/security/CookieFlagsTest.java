package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ApiBrowser;
import ke.sauti.hms.support.MutableClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * The cookie flags as a real server sends them. MockMvc rebuilds {@code Set-Cookie}
 * headers from cookie objects and drops attributes, so these checks use real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
@ActiveProfiles("test")
class CookieFlagsTest {

    private static final String PASSWORD = "Habari-yako-2026";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpGenerator totp;

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String setCookie(HttpResponse<?> response, String name) {
        List<String> values = response.headers().allValues("Set-Cookie");
        return values.stream()
                .filter(value -> value.startsWith(name + "="))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("No Set-Cookie for " + name + " in " + values));
    }

    private static String value(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private HttpResponse<String> postJson(String path, String json, String cookies, String csrfToken) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .header("Cookie", cookies)
                        .header(ApiBrowser.CSRF_HEADER, csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theCsrfCookieIsReadableByThePageSecureAndStrict() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(uri("/actuator/health")).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(setCookie(response, ApiBrowser.CSRF_COOKIE))
                .doesNotContainIgnoringCase("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/");
    }

    @Test
    void theSessionCookieIsHttpOnlySecureAndStrict() throws Exception {
        Staff staff = new Staff(
                "Test Person",
                "staff-" + UUID.randomUUID() + "@example.test",
                null,
                StaffRole.CASHIER,
                null,
                passwordEncoder.encode(PASSWORD));
        staff.startAuthenticatorSetup(SECRET);
        staff.confirmAuthenticator();
        staffRepository.save(staff);

        HttpResponse<String> health = http.send(
                HttpRequest.newBuilder(uri("/actuator/health")).build(), HttpResponse.BodyHandlers.ofString());
        String csrf = value(setCookie(health, ApiBrowser.CSRF_COOKIE));

        HttpResponse<String> login = postJson(
                "/api/auth/login",
                "{\"email\":\"" + staff.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}",
                ApiBrowser.CSRF_COOKIE + "=" + csrf,
                csrf);
        assertThat(login.statusCode()).isEqualTo(200);
        String session = setCookie(login, ApiBrowser.SESSION_COOKIE);

        assertThat(session).contains("HttpOnly").contains("Secure").contains("SameSite=Strict");

        HttpResponse<String> code = postJson(
                "/api/auth/code",
                "{\"code\":\"" + totp.codeFor(SECRET, totp.currentStep()) + "\"}",
                ApiBrowser.SESSION_COOKIE + "=" + value(session) + "; " + ApiBrowser.CSRF_COOKIE + "=" + csrf,
                csrf);
        assertThat(code.statusCode()).isEqualTo(200);
        assertThat(setCookie(code, ApiBrowser.SESSION_COOKIE))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
    }
}
