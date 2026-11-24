package ke.sauti.hms.medication.speech;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import ke.sauti.hms.medication.DoseUnit;
import ke.sauti.hms.medication.Frequency;
import ke.sauti.hms.patient.PreferredLanguage;
import org.springframework.core.io.ClassPathResource;

/**
 * Builds the sentence a patient hears, only from the signed values and the versioned wording in
 * {@code templates/instructions-v1}. The verb follows the route, so a cream is never "swallowed".
 */
public final class InstructionTemplates {

    public static final String VERSION = "instructions-v1";

    private static final Map<PreferredLanguage, Properties> WORDING = Map.of(
            PreferredLanguage.ENGLISH, load("en"),
            PreferredLanguage.SWAHILI, load("sw"));

    private InstructionTemplates() {}

    /** The signed values of one prescription line. */
    public record Line(String medicine, BigDecimal dose, DoseUnit unit, Frequency frequency, int days, String route) {}

    public static String render(Line line, PreferredLanguage language) {
        Properties words = WORDING.get(language);
        boolean single = line.dose().compareTo(BigDecimal.ONE) == 0;
        String unit = word(words, "unit." + line.unit().name() + (single ? ".one" : ".many"));
        String dose = word(words, "dose")
                .replace("{number}", NumberWords.of(line.dose(), language))
                .replace("{unit}", unit);
        String days = line.days() == 1
                ? word(words, "days.one")
                : word(words, "days.many")
                        .replace("{number}", NumberWords.of(BigDecimal.valueOf(line.days()), language));
        // A single dose has no course length: "once, right now, for five days" would contradict itself.
        String sentence = word(words, "line." + line.route());
        if (line.frequency() == Frequency.STAT) {
            sentence = sentence.replace(", {days}", "");
        }
        return sentence.replace("{dose}", dose)
                .replace(
                        "{frequency}",
                        word(words, "frequency." + line.frequency().name()))
                .replace("{days}", days)
                .replace("{medicine}", line.medicine());
    }

    private static String word(Properties words, String key) {
        String value = words.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("No instruction wording for " + key);
        }
        return value;
    }

    private static Properties load(String file) {
        ClassPathResource resource = new ClassPathResource("templates/" + VERSION + "/" + file + ".properties");
        Properties properties = new Properties();
        try (Reader reader = resource.getContentAsString(StandardCharsets.UTF_8).transform(java.io.StringReader::new)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("The instruction wording " + file + " is missing", e);
        }
    }
}
