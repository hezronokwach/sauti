package ke.sauti.hms.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import ke.sauti.hms.common.web.RequestIdFilter;
import ke.sauti.hms.common.web.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * The one place that turns an error into a response. Every exception handler
 * extends this class, so the response shape, request id and logging rules are
 * the same everywhere:
 * <ul>
 *   <li>the body is always an {@link ApiError} with the request id;</li>
 *   <li>client errors (4xx) log one WARN line without a stack trace;</li>
 *   <li>server errors (5xx) log at ERROR with the stack trace;</li>
 *   <li>exception messages and stack traces never reach the response body,
 *       only the message passed in here.</li>
 * </ul>
 */
public abstract class BaseExceptionHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected ResponseEntity<ApiError> respond(
            ErrorCode code, String message, Exception exception, HttpServletRequest request) {
        return respond(code, message, List.of(), exception, request, new HttpHeaders());
    }

    protected ResponseEntity<ApiError> respond(
            ErrorCode code,
            String message,
            List<ApiError.FieldError> fieldErrors,
            Exception exception,
            HttpServletRequest request) {
        return respond(code, message, fieldErrors, exception, request, new HttpHeaders());
    }

    protected ResponseEntity<ApiError> respond(
            ErrorCode code,
            String message,
            List<ApiError.FieldError> fieldErrors,
            Exception exception,
            HttpServletRequest request,
            HttpHeaders headers) {
        String safeMessage = message == null ? code.defaultMessage() : message;
        String requestId = RequestIds.current(request);
        logError(code, safeMessage, exception, request);

        if (requestId != null) {
            headers.set(RequestIdFilter.HEADER, requestId);
        }
        ApiError body = new ApiError(code.name(), safeMessage, fieldErrors, requestId);
        return ResponseEntity.status(code.status()).headers(headers).body(body);
    }

    private void logError(ErrorCode code, String message, Exception exception, HttpServletRequest request) {
        String where = request == null ? "-" : request.getMethod() + " " + request.getRequestURI();
        String type = exception == null ? "-" : exception.getClass().getSimpleName();
        if (code.status().is5xxServerError()) {
            log.error("{} {} {} ({}): {}", code.status().value(), code, where, type, message, exception);
        } else {
            log.warn("{} {} {} ({}): {}", code.status().value(), code, where, type, message);
        }
    }
}
