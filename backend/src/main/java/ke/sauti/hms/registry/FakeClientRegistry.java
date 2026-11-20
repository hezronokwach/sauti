package ke.sauti.hms.registry;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The registry stand-in: the twenty made-up people in {@link FakePeople}, by national ID. */
@Component
@ConditionalOnProperty(name = "sauti.registry.provider", havingValue = "fake", matchIfMissing = true)
public class FakeClientRegistry implements ClientRegistry {

    @Override
    public Optional<Person> search(IdType idType, String idNumber) {
        if (FakePeople.UNAVAILABLE_ID.equals(idNumber)) {
            throw new RegistryUnavailableException("The fake registry is down for this ID");
        }
        if (idType != IdType.NATIONAL_ID) {
            return Optional.empty();
        }
        return Optional.ofNullable(FakePeople.BY_NATIONAL_ID.get(idNumber));
    }
}
