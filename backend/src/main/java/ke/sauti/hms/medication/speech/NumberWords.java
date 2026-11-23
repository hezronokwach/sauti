package ke.sauti.hms.medication.speech;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import ke.sauti.hms.patient.PreferredLanguage;

/**
 * Numbers as spoken words, so a dose is never left to a voice's guess. Digits after the point
 * are read one by one: 0.25 is "zero point two five". Whole numbers above 999,999 are also read
 * digit by digit.
 */
final class NumberWords {

    private static final int MAX_WHOLE = 999_999;
    private static final List<String> EN_SMALL = List.of(
            "zero",
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven",
            "eight",
            "nine",
            "ten",
            "eleven",
            "twelve",
            "thirteen",
            "fourteen",
            "fifteen",
            "sixteen",
            "seventeen",
            "eighteen",
            "nineteen");
    private static final List<String> EN_TENS =
            List.of("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety");
    private static final List<String> SW_DIGITS =
            List.of("sifuri", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa");
    private static final List<String> SW_TENS = List.of(
            "", "kumi", "ishirini", "thelathini", "arobaini", "hamsini", "sitini", "sabini", "themanini", "tisini");

    private NumberWords() {}

    static String of(BigDecimal value, PreferredLanguage language) {
        String plain = value.stripTrailingZeros().toPlainString();
        int point = plain.indexOf('.');
        String whole = point < 0 ? plain : plain.substring(0, point);
        String spoken =
                whole.length() > String.valueOf(MAX_WHOLE).length() ? digits(whole, language) : whole(whole, language);
        if (point < 0) {
            return spoken;
        }
        String word = language == PreferredLanguage.SWAHILI ? "nukta" : "point";
        return spoken + " " + word + " " + digits(plain.substring(point + 1), language);
    }

    private static String whole(String text, PreferredLanguage language) {
        int number = Integer.parseInt(text);
        return language == PreferredLanguage.SWAHILI ? swahili(number) : english(number);
    }

    private static String digits(String text, PreferredLanguage language) {
        List<String> names = language == PreferredLanguage.SWAHILI ? SW_DIGITS : EN_SMALL;
        return text.chars().mapToObj(c -> names.get(c - '0')).collect(Collectors.joining(" "));
    }

    private static String english(int number) {
        if (number >= 1000) {
            int rest = number % 1000;
            String thousands = english(number / 1000) + " thousand";
            return rest == 0 ? thousands : thousands + (rest < 100 ? " and " : " ") + english(rest);
        }
        if (number >= 100) {
            int rest = number % 100;
            String hundreds = EN_SMALL.get(number / 100) + " hundred";
            return rest == 0 ? hundreds : hundreds + " and " + english(rest);
        }
        if (number >= 20) {
            int unit = number % 10;
            return EN_TENS.get(number / 10) + (unit == 0 ? "" : " " + EN_SMALL.get(unit));
        }
        return EN_SMALL.get(number);
    }

    private static String swahili(int number) {
        if (number >= 1000) {
            int rest = number % 1000;
            String thousands = "elfu " + swahili(number / 1000);
            return rest == 0 ? thousands : thousands + (rest < 100 ? " na " : " ") + swahili(rest);
        }
        if (number >= 100) {
            int rest = number % 100;
            String hundreds = "mia " + SW_DIGITS.get(number / 100);
            return rest == 0 ? hundreds : hundreds + (rest < 10 ? " na " : " ") + swahili(rest);
        }
        if (number >= 10) {
            int unit = number % 10;
            return SW_TENS.get(number / 10) + (unit == 0 ? "" : " na " + SW_DIGITS.get(unit));
        }
        return SW_DIGITS.get(number);
    }
}
