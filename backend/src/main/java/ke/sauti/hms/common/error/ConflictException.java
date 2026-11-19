package ke.sauti.hms.common.error;

/** The request conflicts with the current state, for example a duplicate or an already-taken item (409). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
