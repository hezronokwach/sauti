package ke.sauti.hms.visit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Visit history: insert and read only. */
public interface VisitStatusChangeRepository extends Repository<VisitStatusChange, UUID> {

    VisitStatusChange save(VisitStatusChange change);

    List<VisitStatusChange> findByVisitIdOrderByChangedAtAsc(UUID visitId);
}
