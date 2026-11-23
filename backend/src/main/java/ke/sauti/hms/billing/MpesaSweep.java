package ke.sauti.hms.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every minute, catches prompts whose callback never arrived. */
@Component
public class MpesaSweep {

    private final MpesaService mpesa;
    private final boolean enabled;

    public MpesaSweep(MpesaService mpesa, @Value("${sauti.jobs.enabled:true}") boolean enabled) {
        this.mpesa = mpesa;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${sauti.mpesa.sweep-ms:60000}")
    public void run() {
        if (enabled) {
            mpesa.sweep();
        }
    }
}
