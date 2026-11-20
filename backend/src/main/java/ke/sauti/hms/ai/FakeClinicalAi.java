package ke.sauti.hms.ai;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development and test stand-in: one history claim per transcript line, quoting the line
 * itself, for the first five lines. Always passes the quote check, so the screens can be
 * built without an API key.
 */
@Component
@ConditionalOnProperty(name = "sauti.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeClinicalAi implements ClinicalAi {

    private static final int MAX_CLAIMS = 5;
    private static final Pattern LINES = Pattern.compile("\\R");

    @Override
    public List<DraftClaim> summarise(String transcript) {
        return LINES.splitAsStream(transcript)
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .limit(MAX_CLAIMS)
                .map(line -> new DraftClaim("history", "Said: " + line, line))
                .toList();
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
