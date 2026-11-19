package ke.sauti.hms.audit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one way to write the audit log. It joins the caller's transaction and refuses to
 * run without one, so a change that rolls back leaves no false audit row behind.
 * Time, actor, request id and client address are filled in here, never by callers.
 * <p>
 * Callers must keep {@code detail} short and free of clinical text: ids, counts and
 * codes only. It is capped at {@value #MAX_DETAIL_BYTES} bytes.
 */
@Service
public class AuditService {

    static final int MAX_DETAIL_BYTES = 2000;

    private static final JsonMapper JSON = JsonMapper.shared();

    private final AuditEventRepository repository;
    private final CurrentActor currentActor;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, CurrentActor currentActor, Clock clock) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /** Records an event that is not about a particular record. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditAction action, Map<String, ?> detail) {
        return record(action, null, null, null, null, detail);
    }

    /**
     * Records an event about a record, optionally tied to a patient and visit.
     *
     * @param action     what happened
     * @param entityType the kind of record, for example "Staff"; may be null
     * @param entityId   the record's id; may be null
     * @param patientId  the patient concerned; may be null
     * @param visitId    the visit concerned; may be null
     * @param detail     a short note without clinical text; may be null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(
            AuditAction action, String entityType, UUID entityId, UUID patientId, UUID visitId, Map<String, ?> detail) {
        Objects.requireNonNull(action, "action");
        AuditActor actor = currentActor.get().orElse(null);
        RequestInfo request = RequestInfo.current();
        AuditEvent event = new AuditEvent(
                Instant.now(clock),
                actor == null ? null : actor.actorId(),
                actor == null ? null : actor.actorRole(),
                action,
                entityType,
                entityId,
                patientId,
                visitId,
                request.requestId(),
                request.ip(),
                toJson(detail));
        return repository.save(event);
    }

    private static String toJson(Map<String, ?> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        String json = JSON.writeValueAsString(detail);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DETAIL_BYTES) {
            throw new IllegalArgumentException(
                    "Audit detail is longer than " + MAX_DETAIL_BYTES + " bytes; keep it to ids and codes");
        }
        return json;
    }
}
