package ke.sauti.hms.audit;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Finds who is logged in for the current request, if anyone. */
@Component
public class CurrentActor {

    public Optional<AuditActor> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuditActor actor) {
            return Optional.of(actor);
        }
        return Optional.empty();
    }
}
