package ke.sauti.hms.medication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {

    List<PrescriptionItem> findByPrescriptionIdOrderByCreatedAtAsc(UUID prescriptionId);

    Optional<PrescriptionItem> findByIdAndPrescriptionId(UUID id, UUID prescriptionId);
}
