package ke.sauti.hms.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.persistence.JpaAuditingConfiguration;
import ke.sauti.hms.security.totp.TotpGenerator;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The step of a used code reaches the database, so the code stays used after a restart. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    JpaAuditingConfiguration.class,
    FixedClockConfiguration.class,
    TotpGenerator.class,
    CodeVerifier.class
})
@ActiveProfiles("test")
class CodeStepPersistenceTest {

    @Autowired
    private StaffRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TotpGenerator totp;

    @Autowired
    private CodeVerifier verifier;

    @Test
    void aUsedCodeStaysUsedOnceSaved() {
        Staff staff = repository.saveAndFlush(CodeVerifierTest.withSecret());
        String code = totp.codeFor(CodeVerifierTest.SECRET, totp.currentStep());

        assertThat(verifier.verify(staff, code)).isTrue();
        entityManager.flush();
        entityManager.clear();

        Staff reloaded = repository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.getLastCodeStep()).isEqualTo(totp.currentStep());
        assertThat(verifier.verify(reloaded, code)).isFalse();
    }
}
