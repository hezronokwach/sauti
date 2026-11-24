package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WordErrorRateTest {

    @Test
    void normalisingMakesWrittenAndSpokenFormsMatch() {
        assertThat(WordErrorRate.normalise("Temperature 38.6, oxygen 96%."))
                .containsExactly("temperature", "38.6", "oxygen", "96", "percent");
        assertThat(WordErrorRate.normalise("thirty eight point six")).containsExactly("38.6");
        assertThat(WordErrorRate.normalise("Amlodipine 10mg, 1,000 mg"))
                .containsExactly("amlodipine", "10", "milligrams", "1000", "milligrams");
        assertThat(WordErrorRate.normalise("Follow-up?  Ndiyo!")).containsExactly("follow", "up", "ndiyo");
    }

    @Test
    void identicalTextHasNoErrors() {
        WordErrorRate.Result result = WordErrorRate.compare("Sina homa, no allergies.", "sina homa no allergies");
        assertThat(result.edits()).isZero();
        assertThat(result.rate()).isZero();
        assertThat(result.differences()).isEmpty();
    }

    @Test
    void editsCountSubstitutionsDeletionsAndInsertions() {
        // said: the patient has no fever (5 words); heard: "patient has a fever today"
        WordErrorRate.Result result = WordErrorRate.compare("the patient has no fever", "patient has a fever today");
        assertThat(result.referenceWords()).isEqualTo(5);
        assertThat(result.edits()).isEqualTo(3);
        assertThat(result.rate()).isEqualTo(0.6);
    }

    @Test
    void aLostNegationIsListedForCheckingByEye() {
        WordErrorRate.Result result = WordErrorRate.compare("no known allergies", "known allergies");
        assertThat(result.differences()).containsExactly(new WordErrorRate.Difference("no", null));
    }

    @Test
    void numberAndMedicineDifferencesAreListedButOrdinaryWordsAreNot() {
        WordErrorRate.Result result = WordErrorRate.compare(
                "amoxicillin five hundred milligrams twice a day", "augmentin 250 milligrams twice daily");
        assertThat(result.differences())
                .containsExactly(
                        new WordErrorRate.Difference("amoxicillin", "augmentin"),
                        new WordErrorRate.Difference("500", "250"));
    }

    @Test
    void swahiliNegativeVerbsCount() {
        assertThat(WordErrorRate.critical("situmii")).isTrue();
        assertThat(WordErrorRate.critical("hakuna")).isTrue();
        assertThat(WordErrorRate.critical("usinywe")).isTrue();
        assertThat(WordErrorRate.critical("homa")).isFalse();
    }

    @Test
    void speakerLabelsAreDropped() {
        assertThat(WordErrorRate.withoutSpeakers("A: Habari.\nB: Sina homa.\n")).isEqualTo("Habari. Sina homa.");
        assertThat(WordErrorRate.normalise(WordErrorRate.withoutSpeakers("A: one\r\nB: two")))
                .isEqualTo(List.of("12"));
    }
}
