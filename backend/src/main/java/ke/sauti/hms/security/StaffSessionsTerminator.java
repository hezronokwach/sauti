package ke.sauti.hms.security;

import java.util.Set;
import ke.sauti.hms.staff.StaffDisabledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ends every session of a disabled account once the disable has committed. Sessions are
 * indexed by principal name, which is the staff id (see {@link StaffPrincipal#getName()}),
 * so the person's next request finds no session and gets a 401.
 */
@Component
public class StaffSessionsTerminator {

    private static final Logger log = LoggerFactory.getLogger(StaffSessionsTerminator.class);

    private final FindByIndexNameSessionRepository<?> sessions;

    public StaffSessionsTerminator(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStaffDisabled(StaffDisabledEvent event) {
        Set<String> ids =
                sessions.findByPrincipalName(event.staffId().toString()).keySet();
        ids.forEach(sessions::deleteById);
        log.info("Ended {} session(s) of disabled staff {}", ids.size(), event.staffId());
    }
}
