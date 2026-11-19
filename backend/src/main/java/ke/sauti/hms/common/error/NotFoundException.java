package ke.sauti.hms.common.error;

/** The requested record does not exist or is not visible to the caller (404). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
