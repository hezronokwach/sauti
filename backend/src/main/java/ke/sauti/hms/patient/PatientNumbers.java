package ke.sauti.hms.patient;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Patient numbers as people read them: SH-000123 (the database keeps 123). */
public final class PatientNumbers {

    private static final Pattern SH_NUMBER = Pattern.compile("SH-?0*(\\d{1,12})");

    private PatientNumbers() {}

    public static String format(long number) {
        return String.format(Locale.ROOT, "SH-%06d", number);
    }

    /** The number in "SH-000123", "sh123" or "SH-123", or empty if the text is not one. */
    public static Optional<Long> parse(String text) {
        Matcher matcher = SH_NUMBER.matcher(text.trim().toUpperCase(Locale.ROOT));
        return matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }
}
