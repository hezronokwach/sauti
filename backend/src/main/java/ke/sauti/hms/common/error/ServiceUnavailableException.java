package ke.sauti.hms.common.error;

/** An outside service the request depends on is unavailable (503). */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message, cause);
    }
}
