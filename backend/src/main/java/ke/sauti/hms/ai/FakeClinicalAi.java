package ke.sauti.hms.ai;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import ke.sauti.hms.capture.DraftField;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development and test stand-in: one history claim per transcript line, quoting the line
 * itself, for the first five lines, then one planted claim quoting words nobody said, so
 * the screens show both a supported and a rejected claim without an API key.
 */
@Component
@ConditionalOnProperty(name = "sauti.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeClinicalAi implements ClinicalAi {

    private static final int MAX_CLAIMS = 5;
    private static final Pattern LINES = Pattern.compile("\\R");

    /** A claim resting on words nobody said, so every fake summary shows a rejected claim. */
    static final DraftClaim PLANTED =
            new DraftClaim("assessment", "Has had a high fever for ten days", "nina homa kali siku kumi");

    @Override
    public List<DraftClaim> summarise(String transcript) {
        List<DraftClaim> claims = new java.util.ArrayList<>(LINES.splitAsStream(transcript)
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .limit(MAX_CLAIMS)
                .map(line -> new DraftClaim("history", "Said: " + line, line))
                .toList());
        claims.add(PLANTED);
        return claims;
    }

    /** The first line said, as both value and quote. */
    @Override
    public Optional<FieldProposal> draftField(DraftField field, String transcript) {
        return LINES.splitAsStream(transcript)
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .map(line -> new FieldProposal(line.replaceAll("[.!?,]+$", ""), line));
    }

    @Override
    public String fieldPromptVersion() {
        return "fake-field-v1";
    }

    @Override
    public String model() {
        return "fake";
    }

    @Override
    public String promptVersion() {
        return "fake-v1";
    }
}
