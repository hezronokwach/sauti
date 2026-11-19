package ke.sauti.hms.common.error;

import java.util.List;

/**
 * The one error shape every API response uses.
 *
 * @param code        stable machine-readable code, see {@link ErrorCode}
 * @param message     short message safe to show to the user
 * @param fieldErrors per-field problems for validation errors; empty otherwise
 * @param requestId   the request's id, also sent in the X-Request-Id header
 */
public record ApiError(String code, String message, List<FieldError> fieldErrors, String requestId) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** One invalid field and what is wrong with it. */
    public record FieldError(String field, String message) {
    }
}
