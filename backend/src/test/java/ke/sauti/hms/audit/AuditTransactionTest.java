package ke.sauti.hms.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.persistence.JpaAuditingConfiguration;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.FixedClockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audit rows share the fate of the change they describe. These tests manage transactions
 * themselves (no test-wide transaction) and clean up what they commit.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TestcontainersConfiguration.class,
    JpaAuditingConfiguration.class,
    FixedClockConfiguration.class,
    AuditService.class,
    AccessLogService.class,
    CurrentActor.class
})
@ActiveProfiles("test")
class AuditTransactionTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AccessLogService accessLogService;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        AuditTestSupport.clear();
        jdbc.update("delete from access_log");
        jdbc.update("delete from audit_event");
        jdbc.update("delete from staff");
    }

    private long count(String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    @Test
    void anAuditRowCommitsWithTheChange() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> auditService.record(AuditAction.LOGOUT, Map.of("by", "test")));

        assertThat(count("audit_event")).isEqualTo(1);
    }

    @Test
    void aRolledBackChangeLeavesNoAuditRow() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            staffRepository.save(
                    new Staff("Wanjiru Kamau", "wanjiru@example.test", null, StaffRole.RECEPTIONIST, null, "hash"));
            auditService.record(AuditAction.STAFF_CREATED, Map.of("role", "RECEPTIONIST"));
            status.setRollbackOnly(); // the change fails after the audit call
        });

        assertThat(count("staff")).isZero();
        assertThat(count("audit_event")).isZero();
    }

    @Test
    void refusesToRecordOutsideATransaction() {
        assertThatThrownBy(() -> auditService.record(AuditAction.LOGOUT, null))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("audit_event")).isZero();
    }

    @Test
    void aRolledBackChangeLeavesNoAccessLogRow() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Staff doctor = transaction.execute(status -> staffRepository.save(
                new Staff("Amina Otieno", "amina@example.test", null, StaffRole.DOCTOR, "KMPDC-12345", "hash")));
        assertThat(doctor).isNotNull();
        AuditTestSupport.logIn(doctor.getId(), "DOCTOR");

        transaction.executeWithoutResult(status -> {
            accessLogService.record(UUID.randomUUID(), null, AccessKind.NOTE_OPENED);
            status.setRollbackOnly();
        });

        assertThat(count("access_log")).isZero();
    }
}
