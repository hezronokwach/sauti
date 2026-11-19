package ke.sauti.hms.common.error;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return. The code is what the front end switches on;
 * the status is the HTTP status sent with it; the default message is safe to show.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request has invalid fields"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "You need to log in"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have access to this"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This method is not allowed here"),
    CONFLICT(HttpStatus.CONFLICT, "The change conflicts with existing data"),
    STALE_VERSION(HttpStatus.CONFLICT, "This record was changed by someone else; reload and try again"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This content type is not supported"),
    BUSINESS_RULE_VIOLATED(HttpStatus.UNPROCESSABLE_CONTENT, "The request breaks a rule"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests; try again shortly"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A service is unavailable; try again shortly");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** The closest code for a bare HTTP status, used when only the status is known. */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> BUSINESS_RULE_VIOLATED;
            case 429 -> RATE_LIMITED;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status >= 400 && status < 500 ? MALFORMED_REQUEST : INTERNAL_ERROR;
        };
    }
}
