package ke.sauti.hms.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns spoken numbers into digits, in English and Swahili, so "thirty eight point six" and
 * "38.6" count as the same words in the speech evaluation. Only used there.
 *
 * <pre>
 * English  units, teens and tens, "hundred", "thousand", "and" inside a number, "point"
 *          ("one hundred and fifty" = 150, "twelve point five" = 12.5)
 * Swahili  units, kumi and the tens, "mia" and "elfu" followed by how many, "na" between parts,
 *          "nukta" ("thelathini na nne" = 34, "mia moja na hamsini" = 150)
 * Digits   single digits said one after another join up ("three one zero" = 310)
 * </pre>
 *
 * A word that cannot continue the number ends it, so "one sixty" stays "1 60".
 */
final class SpokenNumbers {

    private static final Map<String, Integer> EN_UNITS = Map.ofEntries(
            Map.entry("zero", 0),
            Map.entry("one", 1),
            Map.entry("two", 2),
            Map.entry("three", 3),
            Map.entry("four", 4),
            Map.entry("five", 5),
            Map.entry("six", 6),
            Map.entry("seven", 7),
            Map.entry("eight", 8),
            Map.entry("nine", 9));
    private static final Map<String, Integer> EN_TEENS = Map.ofEntries(
            Map.entry("ten", 10),
            Map.entry("eleven", 11),
            Map.entry("twelve", 12),
            Map.entry("thirteen", 13),
            Map.entry("fourteen", 14),
            Map.entry("fifteen", 15),
            Map.entry("sixteen", 16),
            Map.entry("seventeen", 17),
            Map.entry("eighteen", 18),
            Map.entry("nineteen", 19));
    private static final Map<String, Integer> EN_TENS = Map.of(
            "twenty", 20, "thirty", 30, "forty", 40, "fifty", 50, "sixty", 60, "seventy", 70, "eighty", 80, "ninety",
            90);
    private static final Map<String, Integer> SW_UNITS = Map.of(
            "sifuri", 0, "moja", 1, "mbili", 2, "tatu", 3, "nne", 4, "tano", 5, "sita", 6, "saba", 7, "nane", 8, "tisa",
            9);
    private static final Map<String, Integer> SW_TENS = Map.of(
            "kumi", 10,
            "ishirini", 20,
            "thelathini", 30,
            "arobaini", 40,
            "hamsini", 50,
            "sitini", 60,
            "sabini", 70,
            "themanini", 80,
            "tisini", 90);

    private SpokenNumbers() {}

    /** The same words with every spoken number replaced by its digits. */
    static List<String> toDigits(List<String> words) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < words.size()) {
            Parsed parsed = english(words, i);
            if (parsed == null) {
                parsed = swahili(words, i);
            }
            if (parsed == null) {
                out.add(words.get(i));
                i++;
            } else {
                out.add(parsed.digits());
                i = parsed.next();
            }
        }
        return out;
    }

    /** A number read from the words, and where the words after it start. */
    private record Parsed(String digits, int next) {}

    private enum Last {
        UNIT,
        TEEN,
        TENS,
        HUNDRED,
        THOUSAND
    }

    private static Parsed english(List<String> words, int start) {
        if (!isEnglishNumber(words.get(start))) {
            return null;
        }
        Parsed run = digitRun(words, start, EN_UNITS);
        if (run != null) {
            return run;
        }
        long total = 0;
        long current = 0;
        Last last = null;
        int i = start;
        while (i < words.size()) {
            String word = words.get(i);
            if ("and".equals(word)
                    && (last == Last.HUNDRED || last == Last.THOUSAND)
                    && i + 1 < words.size()
                    && isEnglishNumber(words.get(i + 1))) {
                i++;
                continue;
            }
            Integer unit = EN_UNITS.get(word);
            Integer teen = EN_TEENS.get(word);
            Integer tens = EN_TENS.get(word);
            if (unit != null && (last == null || last == Last.TENS || last == Last.HUNDRED || last == Last.THOUSAND)) {
                current += unit;
                last = Last.UNIT;
            } else if (teen != null && (last == null || last == Last.HUNDRED || last == Last.THOUSAND)) {
                current += teen;
                last = Last.TEEN;
            } else if (tens != null && (last == null || last == Last.HUNDRED || last == Last.THOUSAND)) {
                current += tens;
                last = Last.TENS;
            } else if ("hundred".equals(word) && last != Last.HUNDRED && current % 100 != 0) {
                current *= 100;
                last = Last.HUNDRED;
            } else if ("thousand".equals(word) && last != null && last != Last.THOUSAND) {
                total += current * 1000;
                current = 0;
                last = Last.THOUSAND;
            } else {
                break;
            }
            i++;
        }
        if (last == null) {
            return null;
        }
        String digits = Long.toString(total + current);
        return decimal(words, i, digits, "point", EN_UNITS);
    }

    private static Parsed swahili(List<String> words, int start) {
        String first = words.get(start);
        if (!SW_UNITS.containsKey(first)
                && !SW_TENS.containsKey(first)
                && !"mia".equals(first)
                && !"elfu".equals(first)) {
            return null;
        }
        Parsed run = digitRun(words, start, SW_UNITS);
        if (run != null) {
            return run;
        }
        long total = 0;
        boolean any = false;
        int i = start;
        while (i < words.size()) {
            String word = words.get(i);
            if (any && "na".equals(word) && i + 1 < words.size() && swahiliContinues(words.get(i + 1), total)) {
                i++;
                continue;
            }
            if ("elfu".equals(word) || "mia".equals(word)) {
                long scale = "elfu".equals(word) ? 1000 : 100;
                Integer count = i + 1 < words.size() ? SW_UNITS.get(words.get(i + 1)) : null;
                total += scale * (count == null ? 1 : count);
                i += count == null ? 1 : 2;
            } else if (SW_TENS.containsKey(word)) {
                total += SW_TENS.get(word);
                i++;
            } else if (SW_UNITS.containsKey(word) && (!any || total % 10 == 0)) {
                // A unit only fills an empty ones place: "kumi na mbili", never "mbili na tatu".
                total += SW_UNITS.get(word);
                i++;
            } else {
                break;
            }
            any = true;
        }
        return decimal(words, i, Long.toString(total), "nukta", SW_UNITS);
    }

    /** Two or more single digits in a row, read one by one, as in a phone or patient number. */
    private static Parsed digitRun(List<String> words, int start, Map<String, Integer> units) {
        StringBuilder digits = new StringBuilder();
        int i = start;
        while (i < words.size() && units.containsKey(words.get(i))) {
            digits.append(units.get(words.get(i)));
            i++;
        }
        return digits.length() >= 2 ? new Parsed(digits.toString(), i) : null;
    }

    private static Parsed decimal(List<String> words, int i, String whole, String point, Map<String, Integer> units) {
        if (i + 1 < words.size() && point.equals(words.get(i)) && units.containsKey(words.get(i + 1))) {
            StringBuilder fraction = new StringBuilder();
            int j = i + 1;
            while (j < words.size() && units.containsKey(words.get(j))) {
                fraction.append(units.get(words.get(j)));
                j++;
            }
            return new Parsed(whole + "." + fraction, j);
        }
        return new Parsed(whole, i);
    }

    private static boolean isEnglishNumber(String word) {
        return EN_UNITS.containsKey(word) || EN_TEENS.containsKey(word) || EN_TENS.containsKey(word);
    }

    /** Whether the word after "na" adds to the number, so "mbili na tatu" stays two numbers. */
    private static boolean swahiliContinues(String word, long total) {
        return SW_TENS.containsKey(word)
                || "mia".equals(word)
                || "elfu".equals(word)
                || (SW_UNITS.containsKey(word) && total % 10 == 0);
    }
}
