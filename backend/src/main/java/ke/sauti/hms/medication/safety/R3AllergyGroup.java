package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** R3: the medicine's group matches an allergy, such as amoxicillin with a penicillin allergy. */
@Component
public class R3AllergyGroup implements SafetyRule {

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        for (RuleInput.Line line : input.lines()) {
            for (RuleInput.Allergy allergy : input.allergies()) {
                boolean sameMedicine = input.matcher().namesMedicine(allergy, line.medicine());
                if (!sameMedicine
                        && input.matcher().namesGroup(allergy, line.medicine().getGroupCode())) {
                    findings.add(new Finding(
                            "R3",
                            AlertLevel.BLOCKING,
                            line.item().getId(),
                            "Allergy on file: " + allergy.substance() + ". "
                                    + line.medicine().getName() + " is in the same group."));
                }
            }
        }
        return findings;
    }
}
