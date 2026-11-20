package ke.sauti.hms.patient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PatientNumbersTest {

    @Test
    void showsSixDigitsAtLeast() {
        assertThat(PatientNumbers.format(123)).isEqualTo("SH-000123");
        assertThat(PatientNumbers.format(1234567)).isEqualTo("SH-1234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SH-000123", "sh-123", "SH123", " SH-0123 "})
    void readsTheWaysPeopleTypeIt(String text) {
        assertThat(PatientNumbers.parse(text)).contains(123L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "SH-", "SH-12a", "Shiru", "XSH-123"})
    void ignoresTextThatIsNotOne(String text) {
        assertThat(PatientNumbers.parse(text)).isEmpty();
    }

    @Test
    void escapesLikeWildcardsInNameSearches() {
        assertThat(PatientService.escapeLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }
}
