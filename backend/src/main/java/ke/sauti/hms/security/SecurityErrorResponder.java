package ke.sauti.hms.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Hands requests the security filters turn away (not logged in, wrong role, bad CSRF
 * token) to Spring MVC's exception handling, so they get the same {@code ApiError} body
 * as every other error, built in one place by {@code SecurityExceptionHandler}.
 */
class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;
    private final AccessDeniedAuditor auditor;

    SecurityErrorResponder(HandlerExceptionResolver resolver, AccessDeniedAuditor auditor) {
        this.resolver = resolver;
        this.auditor = auditor;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        respond(request, response, ex, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        if (ex instanceof CsrfException) {
            auditor.csrfRefused();
        }
        respond(request, response, ex, HttpServletResponse.SC_FORBIDDEN);
    }

    private void respond(HttpServletRequest request, HttpServletResponse response, Exception ex, int fallbackStatus)
            throws IOException {
        if (resolver.resolveException(request, response, null, ex) == null) {
            response.sendError(fallbackStatus);
        }
    }
}
