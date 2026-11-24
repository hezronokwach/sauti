package ke.sauti.hms.patient;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.crypto.KeyedHasher;
import ke.sauti.hms.patient.dto.RegisterPatientRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds made-up patients to the demo. This bean exists only when {@code sauti.demo.seed} is
 * true, which only the demo profile sets, so no other deployment can reach it.
 */
@Service
@ConditionalOnProperty(name = "sauti.demo.seed", havingValue = "true")
public class DemoPatients {

    private final PatientService patients;
    private final PatientRepository patientRepository;
    private final PatientAllergyRepository allergies;
    private final KeyedHasher hasher;
    private final AuditService audit;

    public DemoPatients(
            PatientService patients,
            PatientRepository patientRepository,
            PatientAllergyRepository allergies,
            KeyedHasher hasher,
            AuditService audit) {
        this.patients = patients;
        this.patientRepository = patientRepository;
        this.allergies = allergies;
        this.hasher = hasher;
        this.audit = audit;
    }

    /**
     * Registers one person unless their national ID is already there.
     *
     * @param request the made-up person, with a national ID
     * @param penicillinAllergy true to record the allergy the demo script relies on
     * @param recordedBy the admin the allergy entry is recorded under
     * @return true if the person was added
     */
    @Transactional
    public boolean addIfMissing(RegisterPatientRequest request, boolean penicillinAllergy, UUID recordedBy) {
        String nationalId = Objects.requireNonNull(request.nationalId(), "nationalId");
        if (patientRepository.findByNationalIdHash(hasher.hash(nationalId)).isPresent()) {
            return false;
        }
        Patient patient = patients.register(request);
        if (penicillinAllergy) {
            PatientAllergy allergy = allergies.save(
                    PatientAllergy.allergy(patient.getId(), "Penicillin", "PENICILLIN", "Rash", recordedBy));
            audit.record(
                    AuditAction.ALLERGY_RECORDED,
                    PatientAllergy.class.getSimpleName(),
                    allergy.getId(),
                    patient.getId(),
                    null,
                    Map.of("kind", AllergyKind.ALLERGY.name(), "source", "DEMO_SEED"));
        }
        return true;
    }
}
