package ke.sauti.hms.security;

import java.io.Serial;
import ke.sauti.hms.common.error.ApiException;
import ke.sauti.hms.common.error.ErrorCode;

/**
 * A refused login step. It always carries the code's default message, so a failure never
 * says which part (email, password, lock, code) was wrong.
 */
public class LoginException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LoginException(ErrorCode errorCode) {
        super(errorCode, errorCode.defaultMessage());
    }
}
