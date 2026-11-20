package ke.sauti.hms.patient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class KenyanPhoneTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"0712345678", "0712 345 678", "+254712345678", "254712345678", "712345678", "(0712)-345-678"})
    void acceptsTheCommonWaysOfWritingOneNumber(String written) {
        assertThat(KenyanPhone.normalise(written)).contains("254712345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0110123456", "+254110123456"})
    void acceptsTheNewerOnePrefix(String written) {
        assertThat(KenyanPhone.normalise(written)).contains("254110123456");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0201234567", "071234567", "07123456789", "+255712345678", "07123x5678", "abc"})
    void refusesAnythingElse(String written) {
        assertThat(KenyanPhone.normalise(written)).isEmpty();
    }
}
