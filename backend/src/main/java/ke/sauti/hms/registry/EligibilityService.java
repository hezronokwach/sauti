package ke.sauti.hms.registry;

import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.patient.PatientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The eligibility badge: the latest answer, or empty when the patient was never checked. */
@Service
public class EligibilityService {

    private final PatientEligibilityRepository answers;
    private final PatientService patients;

    public EligibilityService(PatientEligibilityRepository answers, PatientService patients) {
        this.answers = answers;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public Optional<PatientEligibility> forPatient(UUID patientId) {
        patients.get(patientId);
        return answers.findByPatientId(patientId);
    }
}
