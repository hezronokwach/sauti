package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TranscriptFilterTest {

    private static SpeechToText.Segment segment(String text, double logprob, double noSpeech, double compression) {
        return new SpeechToText.Segment(text, 0, 1_000, logprob, noSpeech, compression);
    }

    private static SpeechToText.Segment clear(String text) {
        return segment(text, -0.2, 0.02, 1.2);
    }

    @Test
    void silenceIsDropped() {
        assertThat(TranscriptFilter.apply(List.of(segment("mmm", -1.2, 0.8, 1.0)), null))
                .isEmpty();
    }

    @Test
    void quietButConfidentSpeechIsKept() {
        assertThat(TranscriptFilter.apply(List.of(segment("Ndiyo", -0.3, 0.8, 1.0)), null))
                .hasSize(1);
    }

    @Test
    void aRepetitionLoopIsDropped() {
        assertThat(TranscriptFilter.apply(List.of(segment("asante asante asante asante", -0.2, 0.0, 2.5)), null))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Thanks for watching!", "thank you for watching", "Asante kwa kutazama.", "  ", ""})
    void phantomPhrasesAndEmptyTextAreDropped(String text) {
        assertThat(TranscriptFilter.apply(List.of(clear(text)), null)).isEmpty();
    }

    @Test
    void aRepeatOfTheSegmentBeforeIsDropped() {
        List<TranscriptFilter.Kept> kept =
                TranscriptFilter.apply(List.of(clear("Nina homa."), clear("nina homa"), clear("Tangu jana.")), null);

        assertThat(kept).extracting(k -> k.segment().text()).containsExactly("Nina homa.", "Tangu jana.");
    }

    @Test
    void aRepeatAcrossPiecesIsDroppedToo() {
        assertThat(TranscriptFilter.apply(List.of(clear("Tangu jana.")), "tangu jana"))
                .isEmpty();
    }

    @Test
    void clearSpeechIsHigh() {
        assertThat(TranscriptFilter.grade(clear("Kichwa kinauma."))).isEqualTo(Confidence.HIGH);
    }

    @Test
    void unsureSpeechIsReviewOrLow() {
        assertThat(TranscriptFilter.grade(segment("Kichwa kinauma.", -0.6, 0.1, 1.2)))
                .isEqualTo(Confidence.REVIEW);
        assertThat(TranscriptFilter.grade(segment("Kichwa kinauma.", -0.9, 0.1, 1.2)))
                .isEqualTo(Confidence.LOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sina kikohozi.", "Hakuna damu.", "Si mbaya.", "I am not pregnant.", "No pain."})
    void negationsAlwaysNeedReview(String text) {
        assertThat(TranscriptFilter.grade(clear(text))).isEqualTo(Confidence.REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nimemeza Paracetamol.", "Alipewa amoxicillin jana.", "Temperature 38.5.", "Siku 3."})
    void numbersAndMedicinesAlwaysNeedReview(String text) {
        assertThat(TranscriptFilter.grade(clear(text))).isEqualTo(Confidence.REVIEW);
    }

    @Test
    void wordsThatOnlyContainANegationAreNotNegations() {
        assertThat(TranscriptFilter.grade(clear("Asubuhi nilikula vizuri."))).isEqualTo(Confidence.HIGH);
    }

    @Test
    void theFakeTranscriberExercisesEveryRule() {
        List<TranscriptFilter.Kept> kept = TranscriptFilter.apply(FakeSpeechToText.SEGMENTS, null);

        assertThat(kept)
                .extracting(k -> k.segment().text() + " | " + k.confidence())
                .containsExactly(
                        "Habari daktari, nimekuwa na homa tangu jana. | HIGH",
                        "Sina kikohozi. | REVIEW",
                        "Nimemeza paracetamol mara mbili. | REVIEW",
                        "Kichwa kinauma upande wa kushoto. | LOW",
                        "Nina kizunguzungu kidogo asubuhi. | REVIEW");
    }

    @Test
    void thePromptCarriesTheGlossaryAndAtMostTheLast200Characters() {
        String prompt = TranscribeHandler.prompt(List.of());

        assertThat(prompt).startsWith("paracetamol, panadol").contains("amoxicillin");
        assertThat(prompt.length())
                .isLessThanOrEqualTo(MedicineGlossary.promptLine().length() + 2 + 200);
    }
}
