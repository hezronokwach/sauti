package ke.sauti.hms.medication.safety;

import java.util.List;
import org.springframework.stereotype.Component;

/** R7: nobody has recorded either an allergy or "no known allergies". */
@Component
public class R7AllergiesNeverAsked implements SafetyRule {

    @Override
    public List<Finding> check(RuleInput input) {
        if (input.lines().isEmpty() || input.allergiesAsked()) {
            return List.of();
        }
        return List.of(new Finding(
                "R7",
                AlertLevel.REVIEW,
                null,
                "Allergies have not been recorded for this patient. Ask before signing."));
    }
}
