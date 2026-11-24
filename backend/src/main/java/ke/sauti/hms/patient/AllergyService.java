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
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * A patient's allergy list, for the people looking after the patient (see
 * {@link AccessPolicy}). Rules:
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
    private final AccessPolicy accessPolicy;

    public AllergyService(
            PatientAllergyRepository repository,
            PatientRepository patients,
            AuditService audit,
            AccessPolicy accessPolicy) {
        this.repository = repository;
        this.patients = patients;
        this.audit = audit;
        this.accessPolicy = accessPolicy;
    }

    /** The patient's allergies, for someone looking after them; the read is access-logged. */
    public List<PatientAllergy> list(UUID patientId, VisitService.Actor actor) {
        requireAccess(patientId, actor, true);
        return repository.findByPatientIdOrderByCreatedAtAsc(patientId);
    }

    /**
     * The active entries, for the prescription safety rules. Access is checked here; the read
     * is logged when alerts built from it are shown.
     */
    @Transactional(readOnly = true)
    public List<PatientAllergy> active(UUID patientId, VisitService.Actor actor) {
        requireAccess(patientId, actor, false);
        return repository.findByPatientIdOrderByCreatedAtAsc(patientId).stream()
                .filter(PatientAllergy::isActive)
                .toList();
    }

    /**
     * Whether someone has asked the patient about allergies: an active allergy or an active
     * "no known allergies". No access check; it answers yes or no and shows nothing.
     */
    @Transactional(readOnly = true)
    public boolean isRecorded(UUID patientId) {
        return repository.existsByPatientIdAndStatus(patientId, AllergyStatus.ACTIVE);
    }

    public PatientAllergy record(UUID patientId, RecordAllergyRequest request, VisitService.Actor actor) {
        requireAccess(patientId, actor, false);
        UUID staffId = actor.staffId();
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

    public PatientAllergy markEnteredInError(UUID patientId, UUID allergyId, String reason, VisitService.Actor actor) {
        requireAccess(patientId, actor, false);
        UUID staffId = actor.staffId();
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

    /** An unknown patient is 404; otherwise the care-team policy decides (403, audited). */
    private void requireAccess(UUID patientId, VisitService.Actor actor, boolean read) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No such patient");
        }
        accessPolicy.requirePatientAccess(actor, patientId, ClinicalData.ALLERGIES, read);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
