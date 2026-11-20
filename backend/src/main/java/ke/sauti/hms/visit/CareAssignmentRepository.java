package ke.sauti.hms.visit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAssignmentRepository extends JpaRepository<CareAssignment, UUID> {

    List<CareAssignment> findByVisitIdAndEndedAtIsNull(UUID visitId);

    List<CareAssignment> findByVisitIdOrderByStartedAtAsc(UUID visitId);
}
