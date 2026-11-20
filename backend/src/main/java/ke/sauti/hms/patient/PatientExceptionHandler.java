package ke.sauti.hms.patient;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import ke.sauti.hms.common.error.ApiError;
import ke.sauti.hms.common.error.BaseExceptionHandler;
import ke.sauti.hms.common.error.ErrorCode;
import ke.sauti.hms.patient.dto.DuplicatePatientResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The duplicate-patient 409: the standard error, built by {@link BaseExceptionHandler},
 * with the matching patients added. Runs before the general {@code ApiException} handler.
 */
@RestControllerAdvice
@Order(-1)
public class PatientExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(DuplicatePatientException.class)
    ResponseEntity<DuplicatePatientResponse> handleDuplicate(DuplicatePatientException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> standard = respond(ErrorCode.DUPLICATE_PATIENT, ex.getMessage(), ex, request);
        ApiError error = Objects.requireNonNull(standard.getBody(), "respond always sets a body");
        DuplicatePatientResponse body = new DuplicatePatientResponse(
                error.code(),
                error.message(),
                error.fieldErrors(),
                error.requestId(),
                ex.getMatches(),
                ex.isConfirmable());
        return ResponseEntity.status(standard.getStatusCode())
                .headers(standard.getHeaders())
                .body(body);
    }
}
