package ke.sauti.hms.common.error;

import java.util.Objects;

/**
 * Base class for every exception the application throws on purpose.
 * Its message is shown to the user, so it must never contain clinical data,
 * secrets or internal details. Feature code throws one of the subclasses.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    protected ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message == null ? errorCode.defaultMessage() : message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
