package ke.sauti.hms.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SpokenNumbersTest {

    private static String digits(String words) {
        return String.join(" ", SpokenNumbers.toDigits(Arrays.asList(words.split(" "))));
    }

    @Test
    void englishNumbersBecomeDigits() {
        assertThat(digits("take four tablets")).isEqualTo("take 4 tablets");
        assertThat(digits("fifty two per minute")).isEqualTo("52 per minute");
        assertThat(digits("two hundred and fifty milligrams")).isEqualTo("250 milligrams");
        assertThat(digits("one hundred and sixty five centimetres")).isEqualTo("165 centimetres");
        assertThat(digits("one thousand milligrams")).isEqualTo("1000 milligrams");
        assertThat(digits("fourteen days")).isEqualTo("14 days");
    }

    @Test
    void decimalsKeepTheirPoint() {
        assertThat(digits("thirty eight point six")).isEqualTo("38.6");
        assertThat(digits("twelve point five")).isEqualTo("12.5");
    }

    @Test
    void digitsReadOneByOneJoinUp() {
        assertThat(digits("three one zero zero zero zero zero seven")).isEqualTo("31000007");
    }

    @Test
    void aWordThatCannotContinueEndsTheNumber() {
        assertThat(digits("one sixty over one hundred")).isEqualTo("1 60 over 100");
        assertThat(digits("and then")).isEqualTo("and then");
    }

    @Test
    void swahiliNumbersBecomeDigits() {
        assertThat(digits("nina miaka thelathini na nne")).isEqualTo("nina miaka 34");
        assertThat(digits("kilo kumi na mbili")).isEqualTo("kilo 12");
        assertThat(digits("mia moja na hamsini")).isEqualTo("150");
        assertThat(digits("elfu mbili mia tano")).isEqualTo("2500");
        assertThat(digits("mara sita kwa siku")).isEqualTo("mara 6 kwa siku");
    }

    @Test
    void swahiliNaBetweenTwoUnitsStaysAWord() {
        assertThat(digits("mbili na tatu")).isEqualTo("2 na 3");
        assertThat(digits("mama na mtoto")).isEqualTo("mama na mtoto");
    }
}
