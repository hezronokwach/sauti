package ke.sauti.hms.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes {@code ACCESS_DENIED} when a logged-in person is turned away: a role rule (URL
 * or {@code @PreAuthorize}, reported by Spring Security's authorization events) or a bad
 * CSRF token (reported by {@link SecurityErrorResponder}). Anonymous refusals are not
 * written; the login audit rows cover those. The refused request has no transaction of
 * its own, so each row is written in a short one.
 */
@Component
public class AccessDeniedAuditor {

    private static final int MAX_PATH = 200;

    private final AuditService audit;
    private final TransactionTemplate transaction;

    public AccessDeniedAuditor(AuditService audit, PlatformTransactionManager transactionManager) {
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        if (isStaff(event.getAuthentication().get())) {
            record(null);
        }
    }

    /** A logged-in person sent a changing request without a valid CSRF token. */
    void csrfRefused() {
        if (isStaff(SecurityContextHolder.getContext().getAuthentication())) {
            record("CSRF");
        }
    }

    private static boolean isStaff(@Nullable Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof StaffPrincipal;
    }

    private void record(@Nullable String reason) {
        Map<String, String> detail = new LinkedHashMap<>();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            detail.put("method", request.getMethod());
            String path = request.getRequestURI();
            detail.put("path", path.length() > MAX_PATH ? path.substring(0, MAX_PATH) : path);
        }
        if (reason != null) {
            detail.put("reason", reason);
        }
        transaction.executeWithoutResult(status -> audit.record(AuditAction.ACCESS_DENIED, detail));
    }
}
