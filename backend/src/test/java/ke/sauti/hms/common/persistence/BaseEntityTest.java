package ke.sauti.hms.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.facility.FacilitySettings;
import ke.sauti.hms.facility.FacilitySettingsRepository;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

/** BaseEntity's auditing and version column, exercised through the facility settings row. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, FixedClockConfiguration.class})
@ActiveProfiles("test")
class BaseEntityTest {

    @Autowired
    private FacilitySettingsRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void updateStampsTheClockTimeAndBumpsTheVersion() {
        FacilitySettings facility = repository.findCurrent().orElseThrow();
        long versionBefore = facility.getVersion();

        facility.rename("Kisumu Demo Clinic", "KSM-DEMO");
        FacilitySettings saved = repository.saveAndFlush(facility);

        assertThat(saved.getUpdatedAt()).isEqualTo(FixedClockConfiguration.NOW);
        assertThat(saved.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(saved.getCreatedAt()).isNotNull().isNotEqualTo(FixedClockConfiguration.NOW);
    }

    @Test
    void savingAStaleCopyFailsInsteadOfOverwriting() {
        FacilitySettings facility = repository.findCurrent().orElseThrow();
        // Someone else saves the row after we loaded it.
        jdbc.update("update facility_settings set version = version + 1");

        facility.rename("Overwrite Attempt", "LATE");

        assertThatThrownBy(() -> repository.saveAndFlush(facility))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void entitiesWithTheSameIdAreEqual() {
        FacilitySettings first = repository.findCurrent().orElseThrow();
        FacilitySettings second = repository.findById(first.getId()).orElseThrow();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}
