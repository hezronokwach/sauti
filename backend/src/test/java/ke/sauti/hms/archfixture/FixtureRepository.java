package ke.sauti.hms.archfixture;

import java.util.Optional;
import java.util.UUID;
import ke.sauti.hms.facility.FacilitySettings;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** The pretend feature's own repository; never created as a bean. */
@NoRepositoryBean
public interface FixtureRepository extends Repository<FacilitySettings, UUID> {

    Optional<FacilitySettings> findById(UUID id);
}
