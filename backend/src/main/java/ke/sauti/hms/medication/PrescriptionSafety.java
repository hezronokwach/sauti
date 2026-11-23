package ke.sauti.hms.medication;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ke.sauti.hms.medication.safety.AllergyMatcher;
import ke.sauti.hms.medication.safety.Finding;
import ke.sauti.hms.medication.safety.RuleInput;
import ke.sauti.hms.medication.safety.SafetyRule;
import ke.sauti.hms.patient.AllergyKind;
import ke.sauti.hms.patient.AllergyService;
import ke.sauti.hms.patient.PatientAllergy;
import ke.sauti.hms.visit.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs every safety rule on a draft and keeps its alerts in step. A problem that is still there
 * keeps the doctor's decision. A problem that changed, even slightly, is a new open alert, so an
 * old override can never cover something the doctor has not seen.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionSafety {

    private final List<SafetyRule> rules;
    private final SafetyAlertRepository alerts;
    private final PrescriptionItemRepository items;
    private final MedicineRepository medicines;
    private final AllergyGroupRepository groups;
    private final AllergyService allergies;

    public PrescriptionSafety(
            List<SafetyRule> rules,
            SafetyAlertRepository alerts,
            PrescriptionItemRepository items,
            MedicineRepository medicines,
            AllergyGroupRepository groups,
            AllergyService allergies) {
        this.rules = rules;
        this.alerts = alerts;
        this.items = items;
        this.medicines = medicines;
        this.groups = groups;
        this.allergies = allergies;
    }

    /** Runs the rules against the current items and allergies and returns the up-to-date alerts. */
    public List<SafetyAlert> refresh(Prescription prescription, VisitService.Actor actor) {
        List<Finding> findings = new ArrayList<>();
        RuleInput input = input(prescription, actor);
        rules.forEach(rule -> findings.addAll(rule.check(input)));

        List<Finding> unmatched = new ArrayList<>(findings);
        for (SafetyAlert alert : alerts.findByPrescriptionIdOrderByCreatedAtAsc(prescription.getId())) {
            if (!unmatched.removeIf(alert::matches)) {
                alerts.delete(alert);
            }
        }
        alerts.flush();
        unmatched.forEach(finding -> alerts.save(new SafetyAlert(prescription.getId(), finding)));
        alerts.flush();
        return current(prescription.getId());
    }

    /** The stored alerts, most serious first. */
    public List<SafetyAlert> current(UUID prescriptionId) {
        return alerts.findByPrescriptionIdOrderByCreatedAtAsc(prescriptionId).stream()
                .sorted(Comparator.comparing(SafetyAlert::getLevel).thenComparing(SafetyAlert::getRule))
                .toList();
    }

    private RuleInput input(Prescription prescription, VisitService.Actor actor) {
        List<Medicine> list = medicines.findAll();
        Map<UUID, Medicine> byId = list.stream().collect(Collectors.toMap(Medicine::getId, Function.identity()));
        List<RuleInput.Line> lines = items.findByPrescriptionIdOrderByCreatedAtAsc(prescription.getId()).stream()
                .map(item -> new RuleInput.Line(item, byId.get(item.getMedicineId())))
                .toList();
        List<PatientAllergy> active = allergies.active(prescription.getPatientId(), actor);
        List<RuleInput.Allergy> recorded = active.stream()
                .filter(entry -> entry.getKind() == AllergyKind.ALLERGY)
                .map(entry -> new RuleInput.Allergy(entry.getSubstance(), entry.getSubstanceGroup()))
                .toList();
        return new RuleInput(lines, recorded, !active.isEmpty(), new AllergyMatcher(list, groups.findAll()));
    }
}
