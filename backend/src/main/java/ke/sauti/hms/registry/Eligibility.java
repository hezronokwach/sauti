package ke.sauti.hms.registry;

/**
 * SHA eligibility, behind a port: a fake until the real service is connected.
 * Implementations return ELIGIBLE, INELIGIBLE or NOT_FOUND, and throw
 * {@link RegistryUnavailableException} when the service cannot be reached.
 */
public interface Eligibility {

    EligibilityStatus check(String nationalId);
}
