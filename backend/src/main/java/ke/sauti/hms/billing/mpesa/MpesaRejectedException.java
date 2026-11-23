package ke.sauti.hms.billing.mpesa;

/** M-Pesa clearly refused the request, so the customer received no prompt. */
public class MpesaRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MpesaRejectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MpesaRejectedException(String message) {
        super(message);
    }
}
