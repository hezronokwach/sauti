package ke.sauti.hms.registry.dto;

import java.time.LocalDate;
import ke.sauti.hms.patient.Sex;
import ke.sauti.hms.registry.ClientRegistry;
import org.jspecify.annotations.Nullable;

/**
 * A registry lookup: the person's registered details, or found false.
 *
 * @param found whether the registry has this person
 * @param fullName as registered
 * @param dateOfBirth as registered
 * @param sex as registered
 */
public record RegistryLookupResponse(
        boolean found,
        @Nullable String fullName,
        @Nullable LocalDate dateOfBirth,
        @Nullable Sex sex) {

    public static RegistryLookupResponse of(ClientRegistry.Person person) {
        return new RegistryLookupResponse(true, person.fullName(), person.dateOfBirth(), person.sex());
    }

    public static RegistryLookupResponse notFound() {
        return new RegistryLookupResponse(false, null, null, null);
    }
}
