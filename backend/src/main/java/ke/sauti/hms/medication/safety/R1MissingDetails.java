package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.List;
import ke.sauti.hms.medication.PrescriptionItem;
import org.springframework.stereotype.Component;

/** R1: a line without dose, unit, frequency, route or duration. Blocking, with no override. */
@Component
public class R1MissingDetails implements SafetyRule {

    @Override
    public List<Finding> check(RuleInput input) {
        List<Finding> findings = new ArrayList<>();
        for (RuleInput.Line line : input.lines()) {
            PrescriptionItem item = line.item();
            List<String> missing = new ArrayList<>();
            if (item.getDoseValue() == null) {
                missing.add("dose");
            }
            if (item.getDoseUnit() == null) {
                missing.add("unit");
            }
            if (item.getFrequency() == null) {
                missing.add("frequency");
            }
            if (item.getRoute() == null) {
                missing.add("route");
            }
            if (item.getDurationDays() == null) {
                missing.add("duration");
            }
            if (!missing.isEmpty()) {
                findings.add(new Finding(
                        "R1",
                        AlertLevel.BLOCKING,
                        item.getId(),
                        line.medicine().getName() + " is missing: " + String.join(", ", missing) + "."));
            }
        }
        return findings;
    }
}
