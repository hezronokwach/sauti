package ke.sauti.hms.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Exceptions the application throws on purpose: the exception carries its own code. */
@RestControllerAdvice
@Order(0)
public class ApiExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitedException limited) {
            long seconds = Math.max(1, limited.getRetryAfter().toSeconds());
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return respond(ex.getErrorCode(), ex.getMessage(), List.of(), ex, request, headers);
    }
}
