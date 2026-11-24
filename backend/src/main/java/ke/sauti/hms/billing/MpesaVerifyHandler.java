package ke.sauti.hms.billing;

import java.util.UUID;
import ke.sauti.hms.jobs.JobHandler;
import org.springframework.stereotype.Component;

/** Confirms one M-Pesa payment with Safaricom. Running it twice changes nothing. */
@Component
public class MpesaVerifyHandler implements JobHandler {

    private final MpesaService mpesa;

    public MpesaVerifyHandler(MpesaService mpesa) {
        this.mpesa = mpesa;
    }

    @Override
    public String type() {
        return MpesaService.VERIFY_JOB;
    }

    @Override
    public void handle(UUID paymentId) {
        mpesa.verify(paymentId);
    }
}
