package ke.sauti.hms.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Database conflicts (409). Constraint names and SQL never reach the response. */
@RestControllerAdvice
@Order(0)
public class PersistenceExceptionHandler extends BaseExceptionHandler {

    /** Someone saved the same record since it was loaded (version column mismatch). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleStaleVersion(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ErrorCode.STALE_VERSION, null, ex, request);
    }

    /** A unique, foreign key or check constraint rejected the change. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrityViolation(DataIntegrityViolationException ex,
                                                      HttpServletRequest request) {
        return respond(ErrorCode.CONFLICT, null, ex, request);
    }
}
