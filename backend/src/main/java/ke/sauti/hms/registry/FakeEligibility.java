package ke.sauti.hms.registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The eligibility stand-in: the made-up people with odd IDs are eligible, even IDs are not, others are unknown. */
@Component
@ConditionalOnProperty(name = "sauti.registry.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEligibility implements Eligibility {

    @Override
    public EligibilityStatus check(String nationalId) {
        if (FakePeople.UNAVAILABLE_ID.equals(nationalId)) {
            throw new RegistryUnavailableException("The fake eligibility service is down for this ID");
        }
        if (!FakePeople.BY_NATIONAL_ID.containsKey(nationalId)) {
            return EligibilityStatus.NOT_FOUND;
        }
        return FakePeople.eligible(nationalId) ? EligibilityStatus.ELIGIBLE : EligibilityStatus.INELIGIBLE;
    }
}
