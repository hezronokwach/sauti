package ke.sauti.hms.triage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalsRepository extends JpaRepository<Vitals, UUID> {

    List<Vitals> findByVisitIdOrderByRecordedAtAsc(UUID visitId);
}
