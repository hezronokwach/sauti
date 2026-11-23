package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** R4: the same medicine twice, even in a different form or strength. Flags the later line. */
@Component
public class R4Duplicate implements SafetyRule {

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RuleInput.Line line : input.lines()) {
            String name = line.medicine().getName();
            if (!seen.add(name)) {
                findings.add(new Finding(
                        "R4",
                        AlertLevel.REVIEW,
                        line.item().getId(),
                        name + " is on this prescription more than once."));
            }
        }
        return findings;
    }
}
