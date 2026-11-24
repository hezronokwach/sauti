package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R8: an allergy that could not be linked to the medicine list, such as a misspelling
 * ("penicilin"). R2 and R3 cannot protect against it, so the doctor must check it by hand.
 */
@Component
public class R8AllergyNotRecognised implements SafetyRule {

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        if (input.lines().isEmpty()) {
            return findings;
        }
        for (RuleInput.Allergy allergy : input.allergies()) {
            if (!input.matcher().recognises(allergy)) {
                findings.add(new Finding(
                        "R8",
                        AlertLevel.REVIEW,
                        null,
                        "Allergy on file could not be checked automatically: " + allergy.substance()
                                + ". Compare it with this prescription yourself."));
            }
        }
        return findings;
    }
}
