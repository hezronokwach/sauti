package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** R2: the medicine matches an active allergy. */
@Component
public class R2Allergy implements SafetyRule {

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        for (RuleInput.Line line : input.lines()) {
            for (RuleInput.Allergy allergy : input.allergies()) {
                if (input.matcher().namesMedicine(allergy, line.medicine())) {
                    findings.add(new Finding(
                            "R2",
                            AlertLevel.BLOCKING,
                            line.item().getId(),
                            "Allergy on file: " + allergy.substance() + ". This prescription includes "
                                    + line.medicine().getName() + "."));
                }
            }
        }
        return findings;
    }
}
