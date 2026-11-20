package ke.sauti.hms.registry;

import java.io.Serial;

/** The registry or the eligibility service did not answer. Care goes on without it. */
public class RegistryUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RegistryUnavailableException(String message) {
        super(message);
    }
}
