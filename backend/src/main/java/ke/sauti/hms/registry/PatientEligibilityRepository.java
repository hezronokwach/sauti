package ke.sauti.hms.registry;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientEligibilityRepository extends JpaRepository<PatientEligibility, UUID> {

    Optional<PatientEligibility> findByPatientId(UUID patientId);
}
