package ke.sauti.hms.facility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ke.sauti.hms.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FacilitySettingsRepositoryTest {

    @Autowired
    private FacilitySettingsRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedTheFirstMigration() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void loadsTheSeededFacility() {
        FacilitySettings facility = repository.findCurrent().orElseThrow();

        assertThat(facility.getId()).isNotNull();
        assertThat(facility.getName()).isEqualTo("Sauti Demo Clinic");
        assertThat(facility.getCode()).isEqualTo("SAUTI-DEMO");
        assertThat(facility.getCreatedAt()).isNotNull();
        assertThat(facility.getVersion()).isZero();
    }

    @Test
    void allowsOnlyOneFacilityRow() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into facility_settings (name, code) values ('Second Clinic', 'SECOND')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> jdbc.update("update facility_settings set name = '  '"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBlankCode() {
        assertThatThrownBy(() -> jdbc.update("update facility_settings set code = ''"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
