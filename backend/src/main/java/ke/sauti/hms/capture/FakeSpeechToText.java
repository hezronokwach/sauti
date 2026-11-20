package ke.sauti.hms.capture;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A stand-in transcriber for development and tests: every piece gives the same fixed
 * segments, chosen so that every filter and grading rule is exercised (a silent stretch, a
 * repetition loop, a phantom phrase, a repeat, a negation, a number with a medicine, and an
 * unsure stretch). Used unless {@code sauti.transcription.provider} names a real provider.
 */
@Component
@ConditionalOnProperty(name = "sauti.transcription.provider", havingValue = "fake", matchIfMissing = true)
public class FakeSpeechToText implements SpeechToText {

    static final List<Segment> SEGMENTS = List.of(
            new Segment("Habari daktari, nimekuwa na homa tangu jana.", 0, 3_200, -0.21, 0.02, 1.4),
            new Segment("", 3_200, 5_000, -1.40, 0.91, 1.0),
            new Segment("asante asante asante asante asante asante asante asante", 5_000, 7_000, -0.30, 0.05, 3.1),
            new Segment("Thanks for watching!", 7_000, 7_800, -0.35, 0.10, 1.2),
            new Segment("Sina kikohozi.", 7_800, 9_000, -0.25, 0.03, 1.1),
            new Segment("Sina kikohozi.", 9_000, 10_100, -0.27, 0.03, 1.1),
            new Segment("Nimemeza paracetamol mara mbili.", 10_100, 12_500, -0.30, 0.04, 1.3),
            new Segment("Kichwa kinauma upande wa kushoto.", 12_500, 15_000, -0.92, 0.10, 1.2),
            new Segment("Nina kizunguzungu kidogo asubuhi.", 15_000, 17_600, -0.62, 0.08, 1.2));

    @Override
    public List<Segment> transcribe(byte[] audio, String mimeType, String languageHint, String prompt) {
        return SEGMENTS;
    }
}
