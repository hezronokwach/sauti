package ke.sauti.hms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a session 8 hours after login however active it has been, so a shift's session
 * never runs into the next one. The idle timeout (15 minutes) is Spring Session's own;
 * this is the hard cap on top of it. The request then carries on as anonymous and gets
 * the usual 401 wherever a login is needed.
 */
public class SessionAgeLimitFilter extends OncePerRequestFilter {

    /** Session attribute holding the {@link Instant} the code step logged the person in. */
    public static final String LOGIN_AT = SessionAgeLimitFilter.class.getName() + ".loginAt";

    public static final Duration MAX_AGE = Duration.ofHours(8);

    private final Clock clock;

    public SessionAgeLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(LOGIN_AT) instanceof Instant loginAt
                && !clock.instant().isBefore(loginAt.plus(MAX_AGE))) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
