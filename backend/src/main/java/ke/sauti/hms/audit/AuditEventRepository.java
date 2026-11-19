package ke.sauti.hms.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert and read only: there is deliberately no update or delete method. */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAt(String entityType, UUID entityId);

    List<AuditEvent> findByActionOrderByOccurredAt(AuditAction action);
}
