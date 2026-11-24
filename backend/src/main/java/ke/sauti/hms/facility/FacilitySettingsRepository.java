package ke.sauti.hms.facility;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FacilitySettingsRepository extends JpaRepository<FacilitySettings, UUID> {

    /** The facility's settings; the table holds at most one row. */
    @Query("select f from FacilitySettings f")
    Optional<FacilitySettings> findCurrent();
}
