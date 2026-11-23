package ke.sauti.hms.medication;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.sauti.hms.audit.AuditAction;
import ke.sauti.hms.audit.AuditService;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.medication.dto.MedicineResponse;
import ke.sauti.hms.medication.dto.PrescriptionItemRequest;
import ke.sauti.hms.medication.dto.PrescriptionItemResponse;
import ke.sauti.hms.medication.dto.PrescriptionResponse;
import ke.sauti.hms.medication.dto.SafetyAlertResponse;
import ke.sauti.hms.medication.safety.AlertLevel;
import ke.sauti.hms.security.CodeCheck;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.staff.StaffService;
import ke.sauti.hms.visit.AccessPolicy;
import ke.sauti.hms.visit.ClinicalData;
import ke.sauti.hms.visit.Visit;
import ke.sauti.hms.visit.VisitService;
import ke.sauti.hms.visit.VisitStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writing, checking and signing a visit's prescription.
 *
 * <ul>
 *   <li>Only the doctor looking after the visit, while the patient is with them.
 *   <li>Every change takes a row lock and must carry the revision the screen read, so two tabs
 *       never overwrite each other.
 *   <li>The safety rules run on every change and again at signing.
 *   <li>Signing needs every alert dealt with and a fresh authenticator code. A signed
 *       prescription never changes: it is cancelled and written again.
 * </ul>
 */
@Service
@Transactional
public class PrescriptionService {

    /** The medicine list and rules a new prescription is checked against. */
    static final String RULES_VERSION = "medicines-v2";

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MIN_OVERRIDE_REASON = 10;
    private static final String PRESCRIPTION = Prescription.class.getSimpleName();
    private static final String SIGNED = "A signed prescription cannot change; cancel it and write a new one";

    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final MedicineRepository medicines;
    private final VisitService visits;
    private final AccessPolicy accessPolicy;
    private final PrescriptionSafety safety;
    private final SafetyAlertRepository alerts;
    private final StaffService staff;
    private final CodeCheck codeCheck;
    private final AuditService audit;
    private final Clock clock;

    public PrescriptionService(
            PrescriptionRepository prescriptions,
            PrescriptionItemRepository items,
            MedicineRepository medicines,
            VisitService visits,
            AccessPolicy accessPolicy,
            PrescriptionSafety safety,
            SafetyAlertRepository alerts,
            StaffService staff,
            CodeCheck codeCheck,
            AuditService audit,
            Clock clock) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.medicines = medicines;
        this.visits = visits;
        this.accessPolicy = accessPolicy;
        this.safety = safety;
        this.alerts = alerts;
        this.staff = staff;
        this.codeCheck = codeCheck;
        this.audit = audit;
        this.clock = clock;
    }

    /** Medicines whose name contains the text. Shorter searches return nothing. */
    @Transactional(readOnly = true)
    public List<MedicineResponse> searchMedicines(String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        if (needle.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        String escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return medicines.search(escaped, PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(MedicineResponse::of)
                .toList();
    }

    /** The visit's prescription for the care team; the read is access-logged. 404 if none yet. */
    public PrescriptionResponse get(UUID visitId, VisitService.Actor actor) {
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.PRESCRIPTION, true);
        Prescription prescription = prescriptions
                .findActiveByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No prescription for this visit yet"));
        // The patient's allergies may have changed since the last edit.
        if (prescription.isDraft() && actor.role() == StaffRole.DOCTOR) {
            safety.refresh(prescription, actor);
        }
        return respond(prescription, actor);
    }

    /** Adds a medicine, creating the draft with the first one. */
    public PrescriptionResponse addItem(UUID visitId, PrescriptionItemRequest request, VisitService.Actor actor) {
        Visit visit = requireWriter(visitId, actor);
        requireValidMedicine(request);
        Prescription prescription = prescriptions.lockActiveByVisitId(visitId).orElse(null);
        if (prescription == null) {
            if (request.revision() != null) {
                throw stale();
            }
            prescription = prescriptions.saveAndFlush(
                    new Prescription(visitId, visit.getPatientId(), actor.staffId(), RULES_VERSION));
        } else {
            requireOpenDraft(prescription, request.revision());
        }
        PrescriptionItem item = new PrescriptionItem(prescription.getId());
        item.write(request);
        items.save(item);
        return changed(prescription, actor);
    }

    public PrescriptionResponse updateItem(
            UUID visitId, UUID itemId, PrescriptionItemRequest request, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        requireValidMedicine(request);
        Prescription prescription = lockDraft(visitId, request.revision());
        requireItem(prescription, itemId).write(request);
        return changed(prescription, actor);
    }

    public PrescriptionResponse removeItem(UUID visitId, UUID itemId, int revision, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        Prescription prescription = lockDraft(visitId, revision);
        items.delete(requireItem(prescription, itemId));
        return changed(prescription, actor);
    }

    /** Prescribing despite a blocking alert: needs a typed reason and a fresh code, and is audited. */
    public PrescriptionResponse overrideAlert(
            UUID visitId, UUID alertId, String reason, String code, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        Prescription prescription = lockOpenDraft(visitId);
        safety.refresh(prescription, actor);
        SafetyAlert alert = requireOpenAlert(prescription, alertId);
        if (!alert.canBeOverridden()) {
            throw new BusinessRuleException("This alert cannot be overridden");
        }
        String typed = reason.strip();
        if (typed.length() < MIN_OVERRIDE_REASON) {
            throw new BusinessRuleException("Give a reason of at least 10 characters");
        }
        codeCheck.require(requireDoctor(actor), code);
        alert.override(typed, actor.staffId(), clock.instant());
        recordDecision(AuditAction.SAFETY_ALERT_OVERRIDDEN, alert, prescription);
        alerts.flush();
        return respond(prescription, actor);
    }

    public PrescriptionResponse acknowledgeAlert(UUID visitId, UUID alertId, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        Prescription prescription = lockOpenDraft(visitId);
        safety.refresh(prescription, actor);
        SafetyAlert alert = requireOpenAlert(prescription, alertId);
        if (alert.getLevel() != AlertLevel.REVIEW) {
            throw new BusinessRuleException("A blocking alert cannot just be acknowledged");
        }
        alert.acknowledge(actor.staffId(), clock.instant());
        recordDecision(AuditAction.SAFETY_ALERT_ACKNOWLEDGED, alert, prescription);
        alerts.flush();
        return respond(prescription, actor);
    }

    /**
     * Signs the prescription. The rules run once more first. A refusal keeps the refreshed
     * alerts, so the doctor sees what stopped the signature.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public PrescriptionResponse sign(UUID visitId, int revision, String code, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        Prescription prescription = lockDraft(visitId, revision);
        if (items.findByPrescriptionIdOrderByCreatedAtAsc(prescription.getId()).isEmpty()) {
            throw new BusinessRuleException("Add a medicine before signing");
        }
        if (safety.refresh(prescription, actor).stream().anyMatch(SafetyAlert::isOpen)) {
            throw new BusinessRuleException("Deal with every safety alert before signing");
        }
        codeCheck.require(requireDoctor(actor), code);
        prescription.sign(clock.instant());
        Prescription signed = prescriptions.saveAndFlush(prescription);
        audit.record(
                AuditAction.PRESCRIPTION_SIGNED,
                PRESCRIPTION,
                signed.getId(),
                signed.getPatientId(),
                visitId,
                Map.of("rulesVersion", signed.getRulesVersion()));
        return respond(signed, actor);
    }

    /** Cancels a signed prescription so a corrected one can be written. Needs a fresh code. */
    public void cancel(UUID visitId, int revision, String code, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        Prescription prescription = prescriptions
                .lockActiveByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No prescription for this visit yet"));
        if (prescription.isDraft()) {
            throw new ConflictException("Only a signed prescription is cancelled; edit the draft instead");
        }
        if (revision != prescription.getRevision()) {
            throw stale();
        }
        codeCheck.require(requireDoctor(actor), code);
        prescription.cancel(actor.staffId(), clock.instant());
        prescriptions.saveAndFlush(prescription);
        audit.record(
                AuditAction.PRESCRIPTION_CANCELLED,
                PRESCRIPTION,
                prescription.getId(),
                prescription.getPatientId(),
                visitId,
                Map.of("rulesVersion", prescription.getRulesVersion()));
    }

    private Staff requireDoctor(VisitService.Actor actor) {
        return staff.findById(actor.staffId()).orElseThrow(() -> new ForbiddenException("Unknown staff"));
    }

    private Prescription lockOpenDraft(UUID visitId) {
        Prescription prescription = prescriptions
                .lockActiveByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No prescription for this visit yet"));
        if (!prescription.isDraft()) {
            throw new ConflictException(SIGNED);
        }
        return prescription;
    }

    private SafetyAlert requireOpenAlert(Prescription prescription, UUID alertId) {
        SafetyAlert alert = alerts.findByIdAndPrescriptionId(alertId, prescription.getId())
                .orElseThrow(() -> new NotFoundException("This alert no longer applies"));
        if (!alert.isOpen()) {
            throw new ConflictException("This alert has already been dealt with");
        }
        return alert;
    }

    /** The audit row names the rule and level only: the reason stays encrypted on the alert. */
    private void recordDecision(AuditAction action, SafetyAlert alert, Prescription prescription) {
        audit.record(
                action,
                SafetyAlert.class.getSimpleName(),
                alert.getId(),
                prescription.getPatientId(),
                prescription.getVisitId(),
                Map.of("rule", alert.getRule(), "level", alert.getLevel().name()));
    }

    private Visit requireWriter(UUID visitId, VisitService.Actor actor) {
        if (actor.role() != StaffRole.DOCTOR) {
            throw new ForbiddenException("Prescriptions are written by the doctor");
        }
        accessPolicy.requireVisitAccess(actor, visitId, ClinicalData.PRESCRIPTION, false);
        Visit visit = visits.get(visitId);
        if (visit.getStatus() != VisitStatus.WITH_DOCTOR) {
            throw new BusinessRuleException("A prescription can only be changed while the patient is with the doctor");
        }
        return visit;
    }

    /** The medicine must be on the list, and the route, if given, one it can be given by. */
    private void requireValidMedicine(PrescriptionItemRequest request) {
        Medicine medicine = medicines
                .findById(request.medicineId())
                .orElseThrow(() -> new BusinessRuleException("That medicine is not on the list"));
        if (request.route() != null && !medicine.routeList().contains(request.route())) {
            throw new BusinessRuleException(medicine.getName() + " cannot be given by that route");
        }
    }

    private Prescription lockDraft(UUID visitId, @Nullable Integer revision) {
        Prescription prescription = prescriptions
                .lockActiveByVisitId(visitId)
                .orElseThrow(() -> new NotFoundException("No prescription for this visit yet"));
        requireOpenDraft(prescription, revision);
        return prescription;
    }

    private static void requireOpenDraft(Prescription prescription, @Nullable Integer revision) {
        if (!prescription.isDraft()) {
            throw new ConflictException(SIGNED);
        }
        if (revision == null || revision != prescription.getRevision()) {
            throw stale();
        }
    }

    private PrescriptionItem requireItem(Prescription prescription, UUID itemId) {
        return items.findByIdAndPrescriptionId(itemId, prescription.getId())
                .orElseThrow(() -> new NotFoundException("No such medicine on this prescription"));
    }

    private PrescriptionResponse changed(Prescription prescription, VisitService.Actor actor) {
        prescription.itemsChanged();
        items.flush();
        Prescription saved = prescriptions.saveAndFlush(prescription);
        safety.refresh(saved, actor);
        return respond(saved, actor);
    }

    /**
     * Alerts quote the patient's allergies, so only roles allowed to see allergies get them,
     * and showing any is an access-logged allergy read.
     */
    private PrescriptionResponse respond(Prescription prescription, VisitService.Actor actor) {
        List<PrescriptionItem> lines = items.findByPrescriptionIdOrderByCreatedAtAsc(prescription.getId());
        Map<UUID, Medicine> byId =
                medicines
                        .findAllById(lines.stream()
                                .map(PrescriptionItem::getMedicineId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        List<SafetyAlertResponse> shown = List.of();
        if (actor.role() != StaffRole.CASHIER) {
            shown = safety.current(prescription.getId()).stream()
                    .map(SafetyAlertResponse::of)
                    .toList();
            if (!shown.isEmpty()) {
                accessPolicy.requireVisitAccess(actor, prescription.getVisitId(), ClinicalData.ALLERGIES, true);
            }
        }
        return PrescriptionResponse.of(
                prescription,
                lines.stream()
                        .map(line -> PrescriptionItemResponse.of(line, byId.get(line.getMedicineId())))
                        .toList(),
                shown);
    }

    private static OptimisticLockingFailureException stale() {
        return new OptimisticLockingFailureException("The prescription was changed elsewhere since it was loaded");
    }
}
