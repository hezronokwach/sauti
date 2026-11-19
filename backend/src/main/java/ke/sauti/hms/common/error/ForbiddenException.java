package ke.sauti.hms.common.error;

/** The caller is logged in but not allowed to do this (403). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
