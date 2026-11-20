package ke.sauti.hms.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, UUID> {

    List<PatientAllergy> findByPatientIdOrderByCreatedAtAsc(UUID patientId);

    Optional<PatientAllergy> findByIdAndPatientId(UUID id, UUID patientId);
}
