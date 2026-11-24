package ke.sauti.hms.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Word error rate for the speech evaluation: both texts are normalised the same way, then
 * counted with the classic edit-distance table over words.
 *
 * <pre>
 * normalise  lower case; "500mg" becomes "500 milligrams" and "%" "percent"; punctuation goes
 *            (a decimal point between digits stays); spoken numbers become digits
 * edits      d[i][0] = i; d[0][j] = j
 *            d[i][j] = min(d[i-1][j] + 1, d[i][j-1] + 1, d[i-1][j-1] + (words differ ? 1 : 0))
 * rate       edits / words in the reference
 * </pre>
 *
 * Every difference touching a number, a medicine name or a negation is listed for checking by
 * eye, since overall accuracy can look fine while "no allergy" becomes "allergy".
 */
final class WordErrorRate {

    /** Short forms Whisper writes for words the speakers say in full. */
    private static final Map<String, String> UNITS = Map.of(
            "mg", "milligrams",
            "g", "grams",
            "kg", "kilos",
            "cm", "centimetres",
            "ml", "millilitres");

    /** Medicines in the scripts that the transcription glossary does not list. */
    private static final Set<String> MORE_MEDICINES = Set.of("azithromycin", "nitrofurantoin");

    /** English negations, and Swahili ones; Swahili negative verbs start with si-, ha- or usi-. */
    private static final Pattern NEGATION =
            Pattern.compile("no|not|never|none|nothing|si|sina|hana|hakuna|hapana|(si|ha|usi)\\p{L}{2,}");

    private static final Pattern NUMBER_THEN_LETTERS = Pattern.compile("(\\d)(\\p{L})");
    private static final Pattern COMMA_IN_NUMBER = Pattern.compile("(\\d),(\\d)");
    private static final Pattern POINT_IN_NUMBER = Pattern.compile("(\\d)\\.(\\d)");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}\u0001 ]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final Pattern SPEAKER = Pattern.compile("^\\s*[A-Z]\\s*:\\s*");

    private WordErrorRate() {}

    /**
     * How one transcript compares with its reference.
     *
     * @param referenceWords words in the normalised reference
     * @param edits substitutions, deletions and insertions needed to turn one into the other
     * @param differences the differences that touch a number, a medicine or a negation
     */
    record Result(int referenceWords, int edits, List<Difference> differences) {

        /** Edits per reference word; 0 is perfect. */
        double rate() {
            return referenceWords == 0 ? 0 : (double) edits / referenceWords;
        }
    }

    /**
     * One word that differs.
     *
     * @param said the reference word, or null when Whisper added a word
     * @param heard what Whisper wrote, or null when it missed the word
     */
    record Difference(@Nullable String said, @Nullable String heard) {}

    static Result compare(String reference, String heard) {
        List<String> ref = normalise(reference);
        List<String> hyp = normalise(heard);
        int[][] d = new int[ref.size() + 1][hyp.size() + 1];
        for (int i = 0; i <= ref.size(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= hyp.size(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= ref.size(); i++) {
            for (int j = 1; j <= hyp.size(); j++) {
                int same = ref.get(i - 1).equals(hyp.get(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + same);
            }
        }
        return new Result(ref.size(), d[ref.size()][hyp.size()], criticalDifferences(d, ref, hyp));
    }

    static List<String> normalise(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace("%", " percent ");
        lower = COMMA_IN_NUMBER.matcher(lower).replaceAll("$1$2");
        lower = POINT_IN_NUMBER.matcher(lower).replaceAll("$1\u0001$2");
        lower = NUMBER_THEN_LETTERS.matcher(lower).replaceAll("$1 $2");
        lower = NOT_WORD.matcher(lower).replaceAll(" ").replace('\u0001', '.');
        List<String> words = new ArrayList<>();
        Matcher word = WORD.matcher(lower);
        while (word.find()) {
            words.add(UNITS.getOrDefault(word.group(), word.group()));
        }
        return SpokenNumbers.toDigits(words);
    }

    /** Walks back through the table and keeps the differences that change meaning. */
    private static List<Difference> criticalDifferences(int[][] d, List<String> ref, List<String> hyp) {
        List<Difference> found = new ArrayList<>();
        int i = ref.size();
        int j = hyp.size();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && ref.get(i - 1).equals(hyp.get(j - 1)) && d[i][j] == d[i - 1][j - 1]) {
                i--;
                j--;
                continue;
            }
            Difference difference;
            if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1) {
                difference = new Difference(ref.get(i - 1), hyp.get(j - 1));
                i--;
                j--;
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                difference = new Difference(ref.get(i - 1), null);
                i--;
            } else {
                difference = new Difference(null, hyp.get(j - 1));
                j--;
            }
            if (critical(difference.said()) || critical(difference.heard())) {
                found.add(difference);
            }
        }
        return found.reversed();
    }

    static boolean critical(@Nullable String word) {
        if (word == null) {
            return false;
        }
        return DIGIT.matcher(word).find()
                || MedicineGlossary.mentionsMedicine(word)
                || MORE_MEDICINES.contains(word)
                || NEGATION.matcher(word).matches();
    }

    /** The words of a script or reference file, without the A: and B: speaker labels. */
    static String withoutSpeakers(String script) {
        return String.join(
                " ",
                script.lines()
                        .map(line -> SPEAKER.matcher(line).replaceFirst(""))
                        .toList());
    }
}
