package ke.sauti.hms.patient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.patient.dto.RecordAllergyRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * A patient's allergy list. Rules:
 * <ul>
 *   <li>Recording an allergy retires an active "no known allergies" entry, since that is
 *       no longer true.
 *   <li>"No known allergies" cannot be recorded while allergies are active; recording it
 *       twice keeps the one entry.
 *   <li>The same substance cannot be active twice.
 *   <li>Nothing is deleted: wrong entries are marked entered in error, with a reason.
 * </ul>
 */
@Service
@Transactional
public class AllergyService {

    static final String SUPERSEDED = "Replaced by a recorded allergy";
    private static final String ALLERGY = PatientAllergy.class.getSimpleName();

    private final PatientAllergyRepository repository;
    private final PatientRepository patients;
    private final AuditService audit;

    public AllergyService(PatientAllergyRepository repository, PatientRepository patients, AuditService audit) {
        this.repository = repository;
        this.patients = patients;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PatientAllergy> list(UUID patientId) {
        requirePatient(patientId);
        return repository.findByPatientIdOrderByCreatedAtAsc(patientId);
    }

    public PatientAllergy record(UUID patientId, RecordAllergyRequest request, UUID staffId) {
        requirePatient(patientId);
        List<PatientAllergy> active = repository.findByPatientIdOrderByCreatedAtAsc(patientId).stream()
                .filter(PatientAllergy::isActive)
                .toList();
        String substance = blankToNull(request.substance());

        if (request.recordsNoKnownAllergies()) {
            if (substance != null) {
                throw new BusinessRuleException("Record either a substance or no known allergies, not both");
            }
            if (active.stream().anyMatch(a -> a.getKind() == AllergyKind.ALLERGY)) {
                throw new BusinessRuleException(
                        "This patient has allergies recorded; mark them entered in error first if they are wrong");
            }
            return active.stream()
                    .filter(a -> a.getKind() == AllergyKind.NO_KNOWN_ALLERGIES)
                    .findFirst()
                    .orElseGet(() -> saveAndAudit(PatientAllergy.noKnownAllergies(patientId, staffId)));
        }

        if (substance == null) {
            throw new BusinessRuleException("Enter the substance, or record no known allergies");
        }
        if (active.stream().anyMatch(a -> a.isSameSubstance(substance))) {
            throw new ConflictException("This allergy is already recorded");
        }
        active.stream()
                .filter(a -> a.getKind() == AllergyKind.NO_KNOWN_ALLERGIES)
                .forEach(nka -> nka.markEnteredInError(SUPERSEDED, staffId));
        repository.flush();
        return saveAndAudit(PatientAllergy.allergy(
                patientId, substance, blankToNull(request.substanceGroup()), blankToNull(request.reaction()), staffId));
    }

    public PatientAllergy markEnteredInError(UUID patientId, UUID allergyId, String reason, UUID staffId) {
        PatientAllergy allergy = repository
                .findByIdAndPatientId(allergyId, patientId)
                .orElseThrow(() -> new NotFoundException("No such allergy entry for this patient"));
        if (!allergy.isActive()) {
            throw new BusinessRuleException("This entry is already marked entered in error");
        }
        allergy.markEnteredInError(reason, staffId);
        audit.record(
                AuditAction.ALLERGY_ENTERED_IN_ERROR,
                ALLERGY,
                allergy.getId(),
                patientId,
                null,
                Map.of("kind", allergy.getKind().name()));
        return allergy;
    }

    private PatientAllergy saveAndAudit(PatientAllergy allergy) {
        PatientAllergy saved = repository.save(allergy);
        audit.record(
                AuditAction.ALLERGY_RECORDED,
                ALLERGY,
                saved.getId(),
                saved.getPatientId(),
                null,
                Map.of("kind", saved.getKind().name()));
        return saved;
    }

    private void requirePatient(UUID patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No such patient");
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
