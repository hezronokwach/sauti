package ke.sauti.hms.medication.safety;

import java.util.List;
import ke.sauti.hms.medication.Medicine;
import ke.sauti.hms.medication.PrescriptionItem;
import org.jspecify.annotations.Nullable;

/**
 * Everything the rules look at.
 *
 * @param lines the prescription's items with their medicines, in order
 * @param allergies the patient's active allergies
 * @param allergiesAsked whether any active allergy or "no known allergies" entry exists
 * @param matcher links allergy text to medicines and groups
 */
public record RuleInput(List<Line> lines, List<Allergy> allergies, boolean allergiesAsked, AllergyMatcher matcher) {

    /** One prescription line. */
    public record Line(PrescriptionItem item, Medicine medicine) {}

    /** One active allergy, as recorded. */
    public record Allergy(String substance, @Nullable String group) {}
}
