package ke.sauti.hms.billing.mpesa;

import static org.assertj.core.api.Assertions.assertThat;

import ke.sauti.hms.billing.mpesa.MpesaGateway.Outcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DarajaOutcomeTest {

    @ParameterizedTest
    @CsvSource({
        "0, SUCCESS",
        "1, FAILED",
        "1032, FAILED",
        "1037, FAILED",
        "1001, FAILED",
        "2001, FAILED",
        "9999, PROCESSING",
        "'', PROCESSING",
    })
    void anUnknownCodeIsNeverTreatedAsFailed(String code, Outcome expected) {
        assertThat(DarajaMpesaGateway.outcome(code)).isEqualTo(expected);
    }
}
