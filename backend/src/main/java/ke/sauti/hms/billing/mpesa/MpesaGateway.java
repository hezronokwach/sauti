package ke.sauti.hms.billing.mpesa;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** Talks to M-Pesa: sends the prompt to the customer's phone and asks how it ended. */
public interface MpesaGateway {

    /** What the status query can say. */
    enum Outcome {
        SUCCESS,
        FAILED,
        PROCESSING
    }

    /**
     * The answer to a status query.
     *
     * @param amountKes the amount, when the provider's query reports one; Daraja's does not
     * @param receipt the receipt, when the provider's query reports one; Daraja's does not
     */
    record QueryResult(
            Outcome outcome,
            @Nullable BigDecimal amountKes,
            @Nullable String receipt) {}

    /**
     * Sends the payment prompt.
     *
     * @return the provider's checkout request id
     * @throws MpesaRejectedException when the provider refused it, so no prompt was sent
     */
    String stkPush(String phone, BigDecimal amountKes, String reference);

    /** Asks how a prompt ended. Throws if the provider cannot be reached. */
    QueryResult query(String checkoutRequestId);
}
