package ke.sauti.hms.medication.safety;

import java.util.List;

/** One safety check. Rules only report problems: they change nothing and suggest no alternatives. */
public interface SafetyRule {

    List<Finding> check(RuleInput input);
}
