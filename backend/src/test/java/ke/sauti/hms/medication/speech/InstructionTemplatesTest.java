package ke.sauti.hms.medication.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import ke.sauti.hms.medication.DoseUnit;
import ke.sauti.hms.medication.Frequency;
import ke.sauti.hms.patient.PreferredLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class InstructionTemplatesTest {

    private static final List<String> ROUTES = List.of("ORAL", "INHALED", "TOPICAL", "IM", "IV", "SC");

    private static InstructionTemplates.Line line(Frequency frequency) {
        return new InstructionTemplates.Line("Amoxicillin", new BigDecimal("500"), DoseUnit.MG, frequency, 5, "ORAL");
    }

    @ParameterizedTest
    @CsvSource({
        "OD, once a day, mara moja kwa siku",
        "BD, twice a day, mara mbili kwa siku",
        "TDS, three times a day, mara tatu kwa siku",
        "QID, four times a day, mara nne kwa siku",
        "Q8H, every eight hours, kila baada ya saa nane",
        "STAT, 'once, right now', 'mara moja, sasa hivi'",
        "NOCTE, every night, kila usiku",
        "AS_NEEDED, only when needed, inapohitajika tu",
    })
    void rendersEveryFrequencyInBothLanguages(Frequency frequency, String english, String swahili) {
        boolean once = frequency == Frequency.STAT;
        assertThat(InstructionTemplates.render(line(frequency), PreferredLanguage.ENGLISH))
                .isEqualTo("Take five hundred milligrams of Amoxicillin, " + english + (once ? "" : ", for five days")
                        + ".");
        assertThat(InstructionTemplates.render(line(frequency), PreferredLanguage.SWAHILI))
                .isEqualTo(
                        "Meza miligramu mia tano ya Amoxicillin, " + swahili + (once ? "" : ", kwa siku tano") + ".");
    }

    @ParameterizedTest
    @EnumSource(PreferredLanguage.class)
    void everyRouteAndUnitHasWordingAndNoPlaceholderIsLeft(PreferredLanguage language) {
        for (String route : ROUTES) {
            for (DoseUnit unit : DoseUnit.values()) {
                String text = InstructionTemplates.render(
                        new InstructionTemplates.Line("Medicine", BigDecimal.ONE, unit, Frequency.OD, 1, route),
                        language);
                assertThat(text).doesNotContain("{", "}");
            }
        }
    }

    @Test
    void onlyOralMedicinesAreSwallowed() {
        for (String route : ROUTES) {
            InstructionTemplates.Line line =
                    new InstructionTemplates.Line("Medicine", BigDecimal.TEN, DoseUnit.MG, Frequency.OD, 2, route);
            boolean oral = route.equals("ORAL");
            assertThat(InstructionTemplates.render(line, PreferredLanguage.ENGLISH)
                            .startsWith("Take"))
                    .isEqualTo(oral);
            assertThat(InstructionTemplates.render(line, PreferredLanguage.SWAHILI)
                            .startsWith("Meza"))
                    .isEqualTo(oral);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0.25, zero point two five, sifuri nukta mbili tano",
        "2.5, two point five, mbili nukta tano",
        "2.50, two point five, mbili nukta tano",
        "11, eleven, kumi na moja",
        "25, twenty five, ishirini na tano",
        "100, one hundred, mia moja",
        "105, one hundred and five, mia moja na tano",
        "125, one hundred and twenty five, mia moja ishirini na tano",
        "1000, one thousand, elfu moja",
        "1005, one thousand and five, elfu moja na tano",
        "1500, one thousand five hundred, elfu moja mia tano",
    })
    void numbersAreWords(BigDecimal value, String english, String swahili) {
        assertThat(NumberWords.of(value, PreferredLanguage.ENGLISH)).isEqualTo(english);
        assertThat(NumberWords.of(value, PreferredLanguage.SWAHILI)).isEqualTo(swahili);
    }

    @Test
    void ssmlTextCannotChangeTheMarkup() {
        String ssml = AzureTextToSpeech.ssml("A & B <voice>", "sw-KE-ZuriNeural", PreferredLanguage.SWAHILI);
        assertThat(ssml).contains("A &amp; B &lt;voice&gt;").contains("xml:lang=\"sw-KE\"");
    }
}
