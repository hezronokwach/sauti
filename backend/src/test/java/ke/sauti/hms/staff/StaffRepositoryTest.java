package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.persistence.JpaAuditingConfiguration;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, FixedClockConfiguration.class})
@ActiveProfiles("test")
class StaffRepositoryTest {

    @Autowired
    private StaffRepository repository;

    static Staff doctor(String email) {
        return new Staff("Amina Otieno", email, "254712345678", StaffRole.DOCTOR, "KMPDC-12345", "hash");
    }

    @Test
    void savesAndLoadsAStaffMemberWithDefaults() {
        Staff saved = repository.saveAndFlush(doctor("amina@example.test"));

        Staff loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRole()).isEqualTo(StaffRole.DOCTOR);
        assertThat(loaded.getStatus()).isEqualTo(StaffStatus.ACTIVE);
        assertThat(loaded.isMustChangePassword()).isTrue();
        assertThat(loaded.isMfaEnabled()).isFalse();
        assertThat(loaded.getFailedLogins()).isZero();
        assertThat(loaded.getCreatedAt()).isEqualTo(FixedClockConfiguration.NOW);
    }

    @Test
    void storesEmailsTrimmedAndLowerCase() {
        Staff saved = repository.saveAndFlush(doctor("  Amina.Otieno@Example.TEST "));

        assertThat(saved.getEmail()).isEqualTo("amina.otieno@example.test");
    }

    @Test
    void findsByEmailRegardlessOfCase() {
        repository.saveAndFlush(doctor("amina@example.test"));

        assertThat(repository.findByEmailIgnoreCase(" AMINA@example.test")).isPresent();
        assertThat(repository.findByEmailIgnoreCase("someone.else@example.test"))
                .isEmpty();
    }

    @Test
    void rejectsADuplicateEmailInAnotherCase() {
        repository.saveAndFlush(doctor("amina@example.test"));

        assertThatThrownBy(() -> repository.saveAndFlush(doctor("AMINA@example.test")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADoctorWithoutALicence() {
        Staff unlicensed = new Staff("No Licence", "nolicence@example.test", null, StaffRole.DOCTOR, null, "hash");

        assertThatThrownBy(() -> repository.saveAndFlush(unlicensed))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsNonClinicalStaffWithoutALicence() {
        Staff receptionist =
                new Staff("Wanjiru Kamau", "wanjiru@example.test", null, StaffRole.RECEPTIONIST, null, "hash");

        assertThat(repository.saveAndFlush(receptionist).getId()).isNotNull();
    }
}
