package ke.sauti.hms.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert and read only: there is deliberately no update or delete method. */
public interface AccessLogRepository extends Repository<AccessLogEntry, UUID> {

    AccessLogEntry save(AccessLogEntry entry);

    List<AccessLogEntry> findByPatientIdOrderByOccurredAt(UUID patientId);
}
