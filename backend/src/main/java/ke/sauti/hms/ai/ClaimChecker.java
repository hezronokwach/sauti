package ke.sauti.hms.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ke.sauti.hms.capture.Confidence;

/**
 * Plain-Java checks on each AI claim against the transcript. Nothing the model says is
 * trusted: a claim is supported only if its quote is really in the transcript, its negation
 * matches the quote's, and every number in it was said.
 *
 * <ul>
 *   <li><b>Quote present:</b> the quote, ignoring case, punctuation and spacing, appears in
 *       the transcript.
 *   <li><b>Negation consistent:</b> the claim is negative exactly when the quote is ("sina
 *       homa" is no fever, never "has fever").
 *   <li><b>Numbers match:</b> every number in the claim, in digits or words (English or
 *       Swahili), is in the quote.
 *   <li><b>Check audio:</b> the quote comes from a line transcribed with LOW or REVIEW
 *       confidence, so the doctor should listen before accepting.
 * </ul>
 */
public final class ClaimChecker {

    /** One transcript line, as the checks see it. */
    public record Line(String text, Confidence confidence, int startMs, UUID sessionId) {}

    /**
     * The verdict on one claim.
     *
     * @param quotePresent the quote is in the transcript
     * @param negationConsistent the claim and quote agree on negation
     * @param numbersMatch every number in the claim is in the quote
     * @param checkAudio the quote comes from a LOW or REVIEW line
     * @param startMs where in the recording the quote starts, if found
     * @param sessionId which recording the quote is in, if found
     */
    public record Verdict(
            boolean quotePresent,
            boolean negationConsistent,
            boolean numbersMatch,
            boolean checkAudio,
            Integer startMs,
            UUID sessionId) {

        public boolean supported() {
            return quotePresent && negationConsistent && numbersMatch;
        }
    }

    /** English negation in the claim or the quote. */
    private static final Set<String> ENGLISH_NEGATIONS = Set.of(
            "no",
            "not",
            "never",
            "none",
            "nil",
            "without",
            "denies",
            "denied",
            "deny",
            "negative",
            "absent",
            "nothing");

    /** Swahili negation: whole words, and negative verb beginnings such as sija- (I have not yet). */
    private static final Set<String> SWAHILI_NEGATIONS = Set.of(
            "sina", "huna", "hana", "hatuna", "hamna", "hawana", "hakuna", "hapana", "si", "sio", "siyo", "bila");

    private static final List<String> SWAHILI_NEGATIVE_PREFIXES =
            List.of("sija", "haja", "hawaja", "hatuja", "sikuwa", "hakuwa", "hawaku", "sitaki", "hataki", "sipendi");

    private static final Map<String, String> NUMBER_WORDS = Map.ofEntries(
            Map.entry("zero", "0"),
            Map.entry("one", "1"),
            Map.entry("two", "2"),
            Map.entry("three", "3"),
            Map.entry("four", "4"),
            Map.entry("five", "5"),
            Map.entry("six", "6"),
            Map.entry("seven", "7"),
            Map.entry("eight", "8"),
            Map.entry("nine", "9"),
            Map.entry("ten", "10"),
            Map.entry("twice", "2"),
            Map.entry("once", "1"),
            Map.entry("moja", "1"),
            Map.entry("mbili", "2"),
            Map.entry("tatu", "3"),
            Map.entry("nne", "4"),
            Map.entry("tano", "5"),
            Map.entry("sita", "6"),
            Map.entry("saba", "7"),
            Map.entry("nane", "8"),
            Map.entry("tisa", "9"),
            Map.entry("kumi", "10"));

    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:,\\d{3})+");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+(?:[.,]\\d+)?");
    private static final Pattern CONTRACTED_NOT = Pattern.compile("n['’]t\\b");

    private final List<Line> lines;
    private final List<String> normalisedLines;
    private final String transcript;

    public ClaimChecker(List<Line> lines) {
        this.lines = List.copyOf(lines);
        this.normalisedLines = this.lines.stream().map(l -> normalise(l.text())).toList();
        this.transcript = String.join(" ", normalisedLines);
    }

    public Verdict check(String claim, String quote) {
        String q = normalise(quote);
        boolean present = !q.isBlank() && (" " + transcript + " ").contains(" " + q + " ");
        Integer startMs = null;
        UUID sessionId = null;
        boolean checkAudio = false;
        if (present) {
            List<Integer> touched = linesHolding(q);
            if (!touched.isEmpty()) {
                startMs = lines.get(touched.getFirst()).startMs();
                sessionId = lines.get(touched.getFirst()).sessionId();
            }
            checkAudio = touched.stream()
                    .map(i -> lines.get(i).confidence())
                    .anyMatch(c -> c == Confidence.LOW || c == Confidence.REVIEW);
        }
        boolean negation = negated(claim) == negated(quote);
        boolean numbers = numbersIn(quote).containsAll(numbersIn(claim));
        return new Verdict(present, negation, numbers, checkAudio, startMs, sessionId);
    }

    /** The lines the quote lies in: the one that holds it whole, or the run of lines it spans. */
    private List<Integer> linesHolding(String q) {
        for (int i = 0; i < normalisedLines.size(); i++) {
            if ((" " + normalisedLines.get(i) + " ").contains(" " + q + " ")) {
                return List.of(i);
            }
        }
        // Spanning lines: find where the quote starts and ends in the joined transcript, then
        // which lines those characters belong to.
        int from = (" " + transcript + " ").indexOf(" " + q + " ");
        if (from < 0) {
            return List.of();
        }
        int to = from + q.length();
        List<Integer> run = new ArrayList<>();
        int lineStart = 0;
        for (int i = 0; i < normalisedLines.size(); i++) {
            int lineEnd = lineStart + normalisedLines.get(i).length();
            if (lineEnd > from && lineStart < to) {
                run.add(i);
            }
            lineStart = lineEnd + 1;
        }
        return run;
    }

    static boolean negated(String text) {
        if (CONTRACTED_NOT.matcher(text.toLowerCase(Locale.ROOT)).find()) {
            return true;
        }
        for (String word : normalise(text).split(" ", -1)) {
            if (ENGLISH_NEGATIONS.contains(word) || SWAHILI_NEGATIONS.contains(word)) {
                return true;
            }
            for (String prefix : SWAHILI_NEGATIVE_PREFIXES) {
                if (word.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Set<String> numbersIn(String text) {
        Set<String> numbers = new HashSet<>();
        Matcher digits = DIGITS.matcher(text);
        while (digits.find()) {
            numbers.add(canonical(digits.group()));
        }
        for (String word : normalise(text).split(" ", -1)) {
            String value = NUMBER_WORDS.get(word);
            if (value != null) {
                numbers.add(value);
            }
        }
        return numbers;
    }

    private static String canonical(String number) {
        // "1,000" is a thousand; "1,5" is one and a half.
        String n = THOUSANDS.matcher(number).matches() ? number.replace(",", "") : number.replace(',', '.');
        if (n.contains(".")) {
            n = n.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return n.replaceFirst("^0+(?=\\d)", "");
    }

    /** Lower case, letters and digits only (a decimal point between digits is kept), single spaces. */
    static String normalise(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("(?<=\\d)[.,](?=\\d)", "\u0000");
        return lower.replaceAll("[^\\p{L}\\p{N}\\u0000]+", " ")
                .replace('\u0000', '.')
                .strip();
    }
}
