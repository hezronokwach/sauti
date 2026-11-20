package ke.sauti.hms.patient;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Kenyan mobile numbers in one form: {@code 254} followed by 9 digits starting with 7 or
 * 1. Accepts the ways people write them (0712 345 678, +254712345678, 712345678), so the
 * same number always encrypts and hashes the same way.
 */
public final class KenyanPhone {

    private static final Pattern SUBSCRIBER = Pattern.compile("[71]\\d{8}");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-()]");

    private KenyanPhone() {}

    /** The normalised number, or empty if this is not a Kenyan mobile number. */
    public static Optional<String> normalise(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        String subscriber;
        if (digits.length() == 12 && digits.startsWith("254")) {
            subscriber = digits.substring(3);
        } else if (digits.length() == 10 && digits.startsWith("0")) {
            subscriber = digits.substring(1);
        } else {
            subscriber = digits;
        }
        return SUBSCRIBER.matcher(subscriber).matches() ? Optional.of("254" + subscriber) : Optional.empty();
    }
}
