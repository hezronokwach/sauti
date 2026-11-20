package ke.sauti.hms.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import ke.sauti.hms.capture.Confidence;
import org.junit.jupiter.api.Test;

/** One test per check, against a short mixed Swahili and English consultation. */
class ClaimCheckerTest {

    private final ClaimChecker checker = new ClaimChecker(List.of(
            new ClaimChecker.Line("Habari daktari, nina homa siku tatu.", Confidence.HIGH, 0),
            new ClaimChecker.Line("Sina homa usiku, but headache every morning.", Confidence.HIGH, 4_000),
            new ClaimChecker.Line("Nilikunywa Panadol 500 mg mara mbili.", Confidence.REVIEW, 9_000),
            new ClaimChecker.Line("Sijawahi kutapika.", Confidence.LOW, 14_000),
            new ClaimChecker.Line("Hakuna hatari kubwa,", Confidence.HIGH, 17_000),
            new ClaimChecker.Line("ni ugonjwa wa kawaida.", Confidence.HIGH, 19_000)));

    @Test
    void aQuoteThatWasSaidIsPresentWhateverTheCaseAndPunctuation() {
        ClaimChecker.Verdict verdict = checker.check("Fever for three days", "NINA HOMA siku tatu");

        assertThat(verdict.quotePresent()).isTrue();
        assertThat(verdict.supported()).isTrue();
        assertThat(verdict.startMs()).isZero();
    }

    @Test
    void anInventedQuoteIsNotPresent() {
        ClaimChecker.Verdict verdict = checker.check("Chest pain", "nina maumivu ya kifua");

        assertThat(verdict.quotePresent()).isFalse();
        assertThat(verdict.supported()).isFalse();
    }

    @Test
    void partOfAWordIsNotAQuote() {
        assertThat(checker.check("Fever", "oma siku").quotePresent()).isFalse();
    }

    @Test
    void aPlantedHasFeverFromSinaHomaFailsTheNegationCheck() {
        ClaimChecker.Verdict verdict = checker.check("Has fever at night", "Sina homa usiku");

        assertThat(verdict.quotePresent()).isTrue();
        assertThat(verdict.negationConsistent()).isFalse();
        assertThat(verdict.supported()).isFalse();
    }

    @Test
    void aNegativeClaimFromANegativeQuotePasses() {
        assertThat(checker.check("No fever at night", "Sina homa usiku").supported())
                .isTrue();
        assertThat(checker.check("Has not vomited", "Sijawahi kutapika").negationConsistent())
                .isTrue();
    }

    @Test
    void aNegativeClaimFromAPositiveQuoteFails() {
        assertThat(checker.check("Denies fever", "nina homa siku tatu").negationConsistent())
                .isFalse();
    }

    @Test
    void wordsThatOnlyLookNegativeAreNotNegation() {
        // "hatari" is danger, not a negative verb; "hakuna" here is.
        assertThat(ClaimChecker.negated("hatari")).isFalse();
        assertThat(ClaimChecker.negated("siku tatu")).isFalse();
        assertThat(ClaimChecker.negated("Hakuna hatari kubwa")).isTrue();
        assertThat(ClaimChecker.negated("She didn't vomit")).isTrue();
    }

    @Test
    void numbersInTheClaimMustHaveBeenSaidInDigitsOrWords() {
        assertThat(checker.check("Fever for 3 days", "nina homa siku tatu").numbersMatch())
                .isTrue();
        assertThat(checker.check("Panadol 500 mg twice", "Panadol 500 mg mara mbili")
                        .numbersMatch())
                .isTrue();
        assertThat(checker.check("Fever for 5 days", "nina homa siku tatu").numbersMatch())
                .isFalse();
        assertThat(checker.check("Panadol 1000 mg twice", "Panadol 500 mg mara mbili")
                        .numbersMatch())
                .isFalse();
    }

    @Test
    void numbersAreReadTheSameWayOnBothSides() {
        assertThat(ClaimChecker.numbersIn("38.50 degrees")).containsExactly("38.5");
        assertThat(ClaimChecker.numbersIn("1,000 mg")).containsExactly("1000");
        assertThat(ClaimChecker.numbersIn("siku saba")).containsExactly("7");
    }

    @Test
    void aQuoteFromAnUnsureLineAsksTheDoctorToListen() {
        ClaimChecker.Verdict review = checker.check("Took Panadol 500 mg twice", "Panadol 500 mg mara mbili");
        ClaimChecker.Verdict low = checker.check("Has not vomited", "Sijawahi kutapika");
        ClaimChecker.Verdict high = checker.check("Fever for three days", "nina homa siku tatu");

        assertThat(review.checkAudio()).isTrue();
        assertThat(review.startMs()).isEqualTo(9_000);
        assertThat(low.checkAudio()).isTrue();
        assertThat(high.checkAudio()).isFalse();
    }

    @Test
    void aQuoteMayRunAcrossTwoLines() {
        ClaimChecker.Verdict verdict = checker.check("No serious danger", "hakuna hatari kubwa ni ugonjwa wa kawaida");

        assertThat(verdict.quotePresent()).isTrue();
        assertThat(verdict.startMs()).isEqualTo(17_000);
    }
}
