package ke.sauti.hms.medication;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.sauti.hms.common.error.BusinessRuleException;
import ke.sauti.hms.common.error.ConflictException;
import ke.sauti.hms.common.error.ForbiddenException;
import ke.sauti.hms.common.error.NotFoundException;
import ke.sauti.hms.medication.dto.MedicineResponse;
import ke.sauti.hms.medication.dto.PrescriptionItemRequest;
import ke.sauti.hms.medication.dto.PrescriptionItemResponse;
import ke.sauti.hms.medication.dto.PrescriptionResponse;
import ke.sauti.hms.staff.StaffRole;
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
 * Writing a visit's draft prescription.
 *
 * <ul>
 *   <li>Only the doctor looking after the visit, while the patient is with them.
 *   <li>Every change takes a row lock and must carry the revision the screen read, so two tabs
 *       never overwrite each other.
 *   <li>Only a draft changes. Signing and the safety rules come with issue 42.
 * </ul>
 */
@Service
@Transactional
public class PrescriptionService {

    /** The medicine list and rules a new prescription is checked against. */
    static final String RULES_VERSION = "medicines-v1";

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_RESULTS = 20;

    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final MedicineRepository medicines;
    private final VisitService visits;
    private final AccessPolicy accessPolicy;

    public PrescriptionService(
            PrescriptionRepository prescriptions,
            PrescriptionItemRepository items,
            MedicineRepository medicines,
            VisitService visits,
            AccessPolicy accessPolicy) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.medicines = medicines;
        this.visits = visits;
        this.accessPolicy = accessPolicy;
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
        return respond(prescription);
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
        return changed(prescription);
    }

    public PrescriptionResponse updateItem(
            UUID visitId, UUID itemId, PrescriptionItemRequest request, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        requireValidMedicine(request);
        Prescription prescription = lockDraft(visitId, request.revision());
        requireItem(prescription, itemId).write(request);
        return changed(prescription);
    }

    public PrescriptionResponse removeItem(UUID visitId, UUID itemId, int revision, VisitService.Actor actor) {
        requireWriter(visitId, actor);
        Prescription prescription = lockDraft(visitId, revision);
        items.delete(requireItem(prescription, itemId));
        return changed(prescription);
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
            throw new ConflictException("A signed prescription cannot change; cancel it and write a new one");
        }
        if (revision == null || revision != prescription.getRevision()) {
            throw stale();
        }
    }

    private PrescriptionItem requireItem(Prescription prescription, UUID itemId) {
        return items.findByIdAndPrescriptionId(itemId, prescription.getId())
                .orElseThrow(() -> new NotFoundException("No such medicine on this prescription"));
    }

    private PrescriptionResponse changed(Prescription prescription) {
        prescription.itemsChanged();
        items.flush();
        return respond(prescriptions.saveAndFlush(prescription));
    }

    private PrescriptionResponse respond(Prescription prescription) {
        List<PrescriptionItem> lines = items.findByPrescriptionIdOrderByCreatedAtAsc(prescription.getId());
        Map<UUID, Medicine> byId =
                medicines
                        .findAllById(lines.stream()
                                .map(PrescriptionItem::getMedicineId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Medicine::getId, Function.identity()));
        return PrescriptionResponse.of(
                prescription,
                lines.stream()
                        .map(line -> PrescriptionItemResponse.of(line, byId.get(line.getMedicineId())))
                        .toList());
    }

    private static OptimisticLockingFailureException stale() {
        return new OptimisticLockingFailureException("The prescription was changed elsewhere since it was loaded");
    }
}
