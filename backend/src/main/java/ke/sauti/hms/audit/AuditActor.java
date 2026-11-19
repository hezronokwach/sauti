package ke.sauti.hms.audit;

import java.util.UUID;

/**
 * The logged-in person, as the audit log records them. The security principal from
 * the login flow implements this, so audit code never depends on the staff package.
 */
public interface AuditActor {

    UUID actorId();

    String actorRole();
}
