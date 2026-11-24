package ke.sauti.hms.registry;

import java.time.LocalDate;
import java.util.Optional;
import ke.sauti.hms.patient.Sex;

/**
 * The national client registry, behind a port: a fake until the real service is connected.
 * Implementations throw {@link RegistryUnavailableException} when the registry cannot be
 * reached, so reception falls back to typing the details.
 */
public interface ClientRegistry {

    /** The person with this document, or empty if the registry has no such person. */
    Optional<Person> search(IdType idType, String idNumber);

    /**
     * What the registry knows about a person.
     *
     * @param fullName as registered
     * @param dateOfBirth as registered
     * @param sex as registered
     */
    record Person(String fullName, LocalDate dateOfBirth, Sex sex) {}
}
