package ke.sauti.hms.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every opening of clinical data. Like the audit log, it joins the caller's
 * transaction and refuses to run without one. Only logged-in staff open clinical data,
 * so a missing actor is a programming error.
 */
@Service
public class AccessLogService {

    private final AccessLogRepository repository;
    private final CurrentActor currentActor;
    private final Clock clock;

    public AccessLogService(AccessLogRepository repository, CurrentActor currentActor, Clock clock) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccessLogEntry record(UUID patientId, UUID visitId, AccessKind what) {
        Objects.requireNonNull(what, "what");
        AuditActor actor = currentActor
                .get()
                .orElseThrow(() -> new IllegalStateException("Clinical data opened without a logged-in actor"));
        return repository.save(new AccessLogEntry(Instant.now(clock), actor.actorId(), patientId, visitId, what));
    }
}
