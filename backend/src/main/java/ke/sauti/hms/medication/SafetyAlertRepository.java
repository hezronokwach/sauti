package ke.sauti.hms.medication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyAlertRepository extends JpaRepository<SafetyAlert, UUID> {

    List<SafetyAlert> findByPrescriptionIdOrderByCreatedAtAsc(UUID prescriptionId);

    Optional<SafetyAlert> findByIdAndPrescriptionId(UUID id, UUID prescriptionId);
}
