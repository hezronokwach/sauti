package ke.sauti.hms.common.error;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Requests whose shape is wrong: invalid fields, unreadable bodies, bad parameters (400). */
@RestControllerAdvice
@Order(0)
public class ValidationExceptionHandler extends BaseExceptionHandler {

    /** A {@code @Valid} request body failed its constraints. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fields = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fields.add(new ApiError.FieldError(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fields.add(new ApiError.FieldError(error.getObjectName(), error.getDefaultMessage())));
        return respond(ErrorCode.VALIDATION_FAILED, null, fields, ex, request);
    }

    /** Constraints on controller method parameters (path variables, query parameters) failed. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleInvalidParameters(HandlerMethodValidationException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldError> fields = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                fields.add(new ApiError.FieldError(name, error.getDefaultMessage()));
            }
        }
        return respond(ErrorCode.VALIDATION_FAILED, null, fields, ex, request);
    }

    /** Constraints checked by a validated service method failed. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                       HttpServletRequest request) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, null, fields, ex, request);
    }

    /** The body is missing or is not valid JSON for the expected type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, "The request body is missing or is not valid JSON", ex, request);
    }

    /** A path variable or query parameter cannot be converted, for example text where a number is expected. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fields = List.of(new ApiError.FieldError(ex.getName(), "has the wrong format"));
        return respond(ErrorCode.MALFORMED_REQUEST, null, fields, ex, request);
    }

    /** A required query parameter is missing. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                    HttpServletRequest request) {
        List<ApiError.FieldError> fields = List.of(new ApiError.FieldError(ex.getParameterName(), "is required"));
        return respond(ErrorCode.VALIDATION_FAILED, null, fields, ex, request);
    }
}
