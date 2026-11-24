package ke.sauti.hms.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Talks to the API the way the front end's browser does: it keeps every cookie it is
 * given (the {@code SESSION} id and the {@code XSRF-TOKEN}), sends them back, and copies
 * the CSRF cookie into the {@code X-XSRF-TOKEN} header on changing requests, fetching a
 * token first if it has none. No test shortcut for CSRF is involved.
 */
public final class ApiBrowser {

    public static final String SESSION_COOKIE = "SESSION";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public ApiBrowser(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * A matching {@code XSRF-TOKEN} cookie and {@code X-XSRF-TOKEN} header, which is what
     * the double-submit check compares, for one-off requests that skip the full flow.
     * <p>
     * Use this, never Spring Security's {@code csrf()} helper: that one swaps the CSRF
     * token store inside the shared filter for a session-based one, and the swap stays
     * for every later request in the cached test context, breaking real cookie tokens.
     */
    public static RequestPostProcessor validCsrfToken() {
        return request -> {
            String token = UUID.randomUUID().toString();
            Cookie[] existing = request.getCookies();
            Cookie[] cookies = existing == null ? new Cookie[1] : Arrays.copyOf(existing, existing.length + 1);
            cookies[cookies.length - 1] = new Cookie(CSRF_COOKIE, token);
            request.setCookies(cookies);
            request.addHeader(CSRF_HEADER, token);
            return request;
        };
    }

    public ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        String method = request.buildRequest(new MockServletContext()).getMethod();
        if (!SAFE_METHODS.contains(method)) {
            if (!cookies.containsKey(CSRF_COOKIE)) {
                send(get("/actuator/health"));
            }
            Cookie token = cookies.get(CSRF_COOKIE);
            if (token != null) {
                request.header(CSRF_HEADER, token.getValue());
            }
        }
        return sendAsIs(request);
    }

    /** Sends the stored cookies but adds no CSRF header, as a forged cross-site request would. */
    public ResultActions sendWithoutCsrfHeader(MockHttpServletRequestBuilder request) throws Exception {
        return sendAsIs(request);
    }

    private ResultActions sendAsIs(MockHttpServletRequestBuilder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.cookie(cookies.values().toArray(Cookie[]::new));
        }
        ResultActions result = mvc.perform(request);
        remember(result.andReturn().getResponse());
        return result;
    }

    private void remember(MockHttpServletResponse response) {
        for (Cookie cookie : response.getCookies()) {
            if (cookie.getMaxAge() == 0
                    || cookie.getValue() == null
                    || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
    }

    public @Nullable String cookie(String name) {
        Cookie cookie = cookies.get(name);
        return cookie == null ? null : cookie.getValue();
    }

    public String session() {
        String session = cookie(SESSION_COOKIE);
        if (session == null) {
            throw new IllegalStateException("No session cookie");
        }
        return session;
    }
}
