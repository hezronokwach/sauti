package ke.sauti.hms.billing.mpesa;

import java.io.Serial;

/** M-Pesa clearly refused the request, so the customer received no prompt. */
public class MpesaRejectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MpesaRejectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MpesaRejectedException(String message) {
        super(message);
    }
}
