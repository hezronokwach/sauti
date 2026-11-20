package ke.sauti.hms.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Security exceptions (401, 403), whether thrown inside controllers and services or
 * handed over by the security filters, which pass their rejections to Spring MVC.
 */
@RestControllerAdvice
@Order(0)
public class SecurityExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> handleUnauthenticated(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, null, ex, request);
    }

    /** A missing or stale CSRF token: the page should reload, not show "no access". */
    @ExceptionHandler(CsrfException.class)
    ResponseEntity<ApiError> handleCsrf(CsrfException ex, HttpServletRequest request) {
        return respond(ErrorCode.CSRF_TOKEN_INVALID, null, ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, null, ex, request);
    }
}
