package ke.sauti.hms.security;

import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.ratelimit.RateLimits;
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
    private final RateLimits limits;

    public CodeCheck(CodeVerifier verifier, RateLimits limits) {
        this.verifier = verifier;
        this.limits = limits;
    }

    public void require(Staff staff, @Nullable String code) {
        if (!staff.isMfaEnabled()) {
            throw new ForbiddenException("The authenticator is not set up");
        }
        // Only wrong codes count, so a busy doctor signing many records is never slowed down.
        String account = staff.getId() == null ? "" : staff.getId().toString();
        limits.requireAvailable(RateLimits.Limit.WRONG_SIGNING_CODE, account);
        if (!verifier.verify(staff, code)) {
            limits.recordFailure(RateLimits.Limit.WRONG_SIGNING_CODE, account);
            throw new ForbiddenException("The authenticator code is not valid");
        }
    }
}
