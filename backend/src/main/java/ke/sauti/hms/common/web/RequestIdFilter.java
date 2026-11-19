package ke.sauti.hms.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id: reuses a valid incoming X-Request-Id, otherwise makes one.
 * The id goes into the logging context (so every log line carries it), onto the
 * response header, and onto the request as an attribute for error handling.
 * <p>
 * Runs before every other filter, including Spring Security, so even rejected
 * requests carry an id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are reused: never let an id leak into the next request.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * The incoming id if it is a canonical UUID, otherwise a new random one.
     * The header comes from outside, so anything else is discarded rather than logged.
     */
    static String resolve(String incoming) {
        if (incoming != null && incoming.length() == 36) {
            try {
                String canonical = UUID.fromString(incoming).toString();
                if (canonical.equalsIgnoreCase(incoming)) {
                    return canonical;
                }
            } catch (IllegalArgumentException ignored) {
                // not a UUID: fall through and generate one
            }
        }
        return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }
}
