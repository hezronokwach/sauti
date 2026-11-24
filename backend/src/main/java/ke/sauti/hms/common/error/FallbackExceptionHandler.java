package ke.sauti.hms.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Anything no other handler recognised (500). Runs last; the response carries a
 * generic message and the request id, the log carries the stack trace.
 */
@RestControllerAdvice
@Order // default order is the lowest precedence, so this handler runs last
public class FallbackExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.INTERNAL_ERROR, null, ex, request);
    }
}
