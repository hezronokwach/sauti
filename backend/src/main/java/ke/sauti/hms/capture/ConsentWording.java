package ke.sauti.hms.capture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import ke.sauti.hms.patient.PreferredLanguage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The consent wording patients are shown, from versioned files in {@code consent/}. A
 * change of wording is a new version (v2-sw.txt, v2-en.txt), so every decision records
 * exactly which words the patient heard. The current files are demonstration wording.
 */
@Component
public class ConsentWording {

    /** The wording in use now. */
    public static final String CURRENT_VERSION = "v1";

    private final Map<PreferredLanguage, String> texts = new EnumMap<>(PreferredLanguage.class);

    public ConsentWording() {
        for (PreferredLanguage language : PreferredLanguage.values()) {
            texts.put(language, read(fileName(language)));
        }
    }

    /** The full version label recorded with a decision, for example v1-sw. */
    public String versionLabel(PreferredLanguage language) {
        return CURRENT_VERSION + "-" + suffix(language);
    }

    public String text(PreferredLanguage language) {
        return texts.get(language);
    }

    private static String fileName(PreferredLanguage language) {
        return "consent/" + CURRENT_VERSION + "-" + suffix(language) + ".txt";
    }

    private static String suffix(PreferredLanguage language) {
        return switch (language) {
            case SWAHILI -> "sw";
            case ENGLISH -> "en";
        };
    }

    private static String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Consent wording missing: " + path.toLowerCase(Locale.ROOT), e);
        }
    }
}
