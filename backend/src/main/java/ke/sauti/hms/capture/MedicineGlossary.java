package ke.sauti.hms.capture;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Common medicine names, used twice: as a prompt that helps the transcriber spell them,
 * and to mark segments mentioning one for review, since a misheard medicine is dangerous.
 * The medicine list in issue 41 will replace this short list.
 */
public final class MedicineGlossary {

    static final List<String> NAMES = List.of(
            "paracetamol",
            "panadol",
            "ibuprofen",
            "amoxicillin",
            "augmentin",
            "ciprofloxacin",
            "metronidazole",
            "flagyl",
            "artemether",
            "lumefantrine",
            "coartem",
            "metformin",
            "amlodipine",
            "omeprazole",
            "salbutamol",
            "cotrimoxazole",
            "doxycycline",
            "ORS",
            "zinc");

    private static final Pattern ANY_NAME = Pattern.compile(
            "\\b(" + String.join("|", NAMES.stream().map(Pattern::quote).toList()) + ")\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private MedicineGlossary() {}

    /** The names as one prompt line for the transcriber. */
    static String promptLine() {
        return String.join(", ", NAMES);
    }

    static boolean mentionsMedicine(String text) {
        return ANY_NAME.matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}
