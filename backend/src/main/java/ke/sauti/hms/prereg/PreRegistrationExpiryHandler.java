package ke.sauti.hms.prereg;

import java.util.UUID;
import ke.sauti.hms.jobs.JobHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a pre-registration at the end of its day, used or not: by then anything reception
 * needed is in the patient record. Safe to run twice: a second run finds nothing to delete.
 */
@Component
public class PreRegistrationExpiryHandler implements JobHandler {

    public static final String TYPE = "PRE_REGISTRATION_EXPIRY";

    private final PreRegistrationRepository repository;

    public PreRegistrationExpiryHandler(PreRegistrationRepository repository) {
        this.repository = repository;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    @Transactional
    public void handle(UUID payloadId) {
        repository.findById(payloadId).ifPresent(repository::delete);
    }
}
