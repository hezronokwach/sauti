package ke.sauti.hms.staff;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.crypto.ColumnCipher;
import ke.sauti.hms.common.persistence.JpaAuditingConfiguration;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The authenticator secret is encrypted in PostgreSQL and plain text in the entity. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, FixedClockConfiguration.class})
@ActiveProfiles("test")
class StaffSecretEncryptionTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Autowired
    private StaffRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ColumnCipher cipher;

    private Staff savedWithSecret() {
        Staff staff = StaffRepositoryTest.doctor("amina@example.test");
        staff.startAuthenticatorSetup(SECRET);
        Staff saved = repository.saveAndFlush(staff);
        entityManager.clear();
        return saved;
    }

    @Test
    void theRawColumnHoldsUnreadableBytes() {
        Staff saved = savedWithSecret();

        byte[] raw = jdbc.queryForObject("select mfa_secret from staff where id = ?", byte[].class, saved.getId());

        assertThat(raw).isNotNull();
        assertThat(raw[0]).isEqualTo((byte) 1);
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
        assertThat(new String(cipher.decrypt(raw), StandardCharsets.UTF_8)).isEqualTo(SECRET);
    }

    @Test
    void theEntityReadsBackThePlainSecret() {
        Staff saved = savedWithSecret();

        Staff loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getMfaSecret()).isEqualTo(SECRET);
    }

    @Test
    void aStaffMemberWithoutASecretStoresNull() {
        Staff saved = repository.saveAndFlush(StaffRepositoryTest.doctor("nosecret@example.test"));

        Long nulls = jdbc.queryForObject(
                "select count(*) from staff where id = ? and mfa_secret is null", Long.class, saved.getId());

        assertThat(nulls).isEqualTo(1);
    }
}
