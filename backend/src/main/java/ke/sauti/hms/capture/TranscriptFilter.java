package ke.sauti.hms.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What to keep from a transcriber's output, and how far to trust it.
 *
 * <pre>
 * DROP   silence (no_speech_prob > 0.6 and avg_logprob < -1.0), a repetition loop
 *        (compression_ratio > 2.4), a phantom phrase ("thanks for watching"), empty text,
 *        or a repeat of the segment before it
 * GRADE  LOW if avg_logprob < -0.8; REVIEW if < -0.5, or the text has a number, a medicine
 *        name or a negation (si, sina, hakuna, no, not), since mishearing those changes the
 *        meaning; otherwise HIGH
 * </pre>
 */
public final class TranscriptFilter {

    static final double SILENCE_NO_SPEECH = 0.6;
    static final double SILENCE_LOGPROB = -1.0;
    static final double LOOP_COMPRESSION = 2.4;
    static final double LOW_LOGPROB = -0.8;
    static final double REVIEW_LOGPROB = -0.5;

    /** Phrases Whisper is known to invent over silence or noise. */
    static final Set<String> PHANTOM_PHRASES = Set.of(
            "thanks for watching",
            "thank you for watching",
            "please subscribe",
            "subscribe to my channel",
            "asante kwa kutazama");

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern NEGATION =
            Pattern.compile("\\b(si|sina|hakuna|hapana|no|not|never)\\b", Pattern.UNICODE_CASE);
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N} ]");

    private TranscriptFilter() {}

    /** A segment worth keeping, with its grade. */
    record Kept(SpeechToText.Segment segment, Confidence confidence) {}

    /**
     * Filters and grades one piece's segments.
     *
     * @param previousText the last kept segment before this piece, so a repeat across pieces is dropped too
     */
    static List<Kept> apply(List<SpeechToText.Segment> segments, String previousText) {
        List<Kept> kept = new ArrayList<>();
        String last = normalise(previousText);
        for (SpeechToText.Segment segment : segments) {
            String text = normalise(segment.text());
            if (shouldDrop(segment, text, last)) {
                continue;
            }
            kept.add(new Kept(segment, grade(segment)));
            last = text;
        }
        return kept;
    }

    static boolean shouldDrop(SpeechToText.Segment segment, String normalised, String previous) {
        boolean silent = segment.noSpeechProb() > SILENCE_NO_SPEECH && segment.avgLogprob() < SILENCE_LOGPROB;
        boolean loop = segment.compressionRatio() > LOOP_COMPRESSION;
        boolean phantom = PHANTOM_PHRASES.contains(normalised);
        boolean repeat = !normalised.isEmpty() && normalised.equals(previous);
        return normalised.isEmpty() || silent || loop || phantom || repeat;
    }

    static Confidence grade(SpeechToText.Segment segment) {
        if (segment.avgLogprob() < LOW_LOGPROB) {
            return Confidence.LOW;
        }
        String lower = segment.text().toLowerCase(Locale.ROOT);
        boolean meaningCritical = DIGIT.matcher(lower).find()
                || MedicineGlossary.mentionsMedicine(lower)
                || NEGATION.matcher(lower).find();
        if (segment.avgLogprob() < REVIEW_LOGPROB || meaningCritical) {
            return Confidence.REVIEW;
        }
        return Confidence.HIGH;
    }

    /** Lower case, punctuation removed, spaces collapsed: for comparing segments. */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return NOT_WORD.matcher(text.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
