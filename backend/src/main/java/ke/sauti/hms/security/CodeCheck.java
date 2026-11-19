package ke.sauti.hms.security;

import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.staff.Staff;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A fresh authenticator code as a guard, for actions that need one even from a logged-in
 * person (signing a note, overriding an allergy block). Throws 403 on failure.
 */
@Component
public class CodeCheck {

    private final CodeVerifier verifier;

    public CodeCheck(CodeVerifier verifier) {
        this.verifier = verifier;
    }

    public void require(Staff staff, @Nullable String code) {
        if (!staff.isMfaEnabled()) {
            throw new ForbiddenException("The authenticator is not set up");
        }
        if (!verifier.verify(staff, code)) {
            throw new ForbiddenException("The authenticator code is not valid");
        }
    }
}
