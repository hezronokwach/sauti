package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * R5: two different medicines from one group. Flags the later line. Vitamins and minerals are
 * not one kind of medicine, so zinc with iron is not flagged.
 */
@Component
public class R5SameGroup implements SafetyRule {

    private static final String NOT_A_DRUG_CLASS = "SUPPLEMENT";

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        Map<String, String> firstInGroup = new HashMap<>();
        for (RuleInput.Line line : input.lines()) {
            String group = line.medicine().getGroupCode();
            String name = line.medicine().getName();
            if (NOT_A_DRUG_CLASS.equals(group)) {
                continue;
            }
            String first = firstInGroup.putIfAbsent(group, name);
            if (first != null && !first.equals(name)) {
                findings.add(new Finding(
                        "R5",
                        AlertLevel.REVIEW,
                        line.item().getId(),
                        name + " and " + first + " are in the same group."));
            }
        }
        return findings;
    }
}
