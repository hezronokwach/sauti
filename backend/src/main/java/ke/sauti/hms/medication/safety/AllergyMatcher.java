package ke.sauti.hms.medication.safety;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ke.sauti.hms.medication.AllergyGroup;
import ke.sauti.hms.medication.Medicine;

/**
 * Links free-text allergies to the medicine list. It matches whole words, ignoring case and
 * punctuation, so "Amoxicillin (rash)" names amoxicillin. It does not guess at spelling: text
 * it cannot link is reported by rule R8 for the doctor to check by hand.
 */
public final class AllergyMatcher {

    /** Everyday words for a group that its code and label do not cover. */
    private static final Map<String, List<String>> ALIASES = Map.of(
            "SULFONAMIDE", List.of("sulfa", "sulpha", "sulphonamide", "sulphonamides"),
            "NSAID", List.of("nsaids", "non steroidal", "nonsteroidal"));

    private final Map<String, String> groupOfIngredient = new HashMap<>();
    private final Map<String, Set<String>> termsOfGroup = new HashMap<>();

    public AllergyMatcher(List<Medicine> medicines, List<AllergyGroup> groups) {
        for (Medicine medicine : medicines) {
            for (String ingredient : ingredients(medicine)) {
                groupOfIngredient.put(ingredient, medicine.getGroupCode());
            }
        }
        for (AllergyGroup group : groups) {
            Set<String> terms = new HashSet<>();
            terms.add(normalise(group.getCode()));
            String label = normalise(group.getLabel());
            terms.add(label);
            if (label.endsWith("s")) {
                terms.add(label.substring(0, label.length() - 1));
            }
            ALIASES.getOrDefault(group.getCode(), List.of()).forEach(alias -> terms.add(normalise(alias)));
            termsOfGroup.put(group.getCode(), terms);
        }
    }

    /** Whether the allergy names this medicine or one of its ingredients. */
    public boolean namesMedicine(RuleInput.Allergy allergy, Medicine medicine) {
        return ingredients(medicine).stream().anyMatch(ingredient -> mentions(allergy.substance(), ingredient));
    }

    /** Whether the allergy names this group, or a medicine that belongs to it. */
    public boolean namesGroup(RuleInput.Allergy allergy, String groupCode) {
        for (String text : texts(allergy)) {
            if (termsOfGroup.getOrDefault(groupCode, Set.of()).stream().anyMatch(term -> mentions(text, term))) {
                return true;
            }
            for (Map.Entry<String, String> ingredient : groupOfIngredient.entrySet()) {
                if (ingredient.getValue().equals(groupCode) && mentions(text, ingredient.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the allergy could be linked to anything on the list. */
    public boolean recognises(RuleInput.Allergy allergy) {
        for (String text : texts(allergy)) {
            boolean ingredient = groupOfIngredient.keySet().stream().anyMatch(name -> mentions(text, name));
            boolean group = termsOfGroup.values().stream().flatMap(Set::stream).anyMatch(term -> mentions(text, term));
            if (ingredient || group) {
                return true;
            }
        }
        return false;
    }

    private static List<String> texts(RuleInput.Allergy allergy) {
        List<String> texts = new ArrayList<>();
        texts.add(allergy.substance());
        if (allergy.group() != null) {
            texts.add(allergy.group());
        }
        return texts;
    }

    /** "Amoxicillin with clavulanic acid" has two ingredients. */
    private static List<String> ingredients(Medicine medicine) {
        return List.of(normalise(medicine.getName()).split(" with ", -1));
    }

    private static boolean mentions(String text, String term) {
        return (" " + normalise(text) + " ").contains(" " + term + " ");
    }

    private static String normalise(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
