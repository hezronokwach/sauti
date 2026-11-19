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
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** What an audit row and an access-log row contain. Rollback behaviour is in AuditTransactionTest. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    JpaAuditingConfiguration.class,
    FixedClockConfiguration.class,
    AuditService.class,
    AccessLogService.class,
    CurrentActor.class
})
@ActiveProfiles("test")
class AuditServiceTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AccessLogService accessLogService;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        AuditTestSupport.clear();
    }

    private Staff doctor() {
        return staffRepository.saveAndFlush(
                new Staff("Amina Otieno", "amina@example.test", null, StaffRole.DOCTOR, "KMPDC-12345", "hash"));
    }

    @Test
    void fillsInTimeActorRequestIdAndAddress() {
        Staff doctor = doctor();
        AuditTestSupport.logIn(doctor.getId(), "DOCTOR");
        AuditTestSupport.inRequest();
        UUID patientId = UUID.randomUUID();

        AuditEvent event = auditService.record(
                AuditAction.ACCESS_DENIED,
                "Visit",
                UUID.randomUUID(),
                patientId,
                null,
                Map.of("reason", "NOT_ASSIGNED"));
        entityManager.flush();

        assertThat(event.getId()).isNotNull();
        assertThat(event.getOccurredAt()).isEqualTo(FixedClockConfiguration.NOW);
        assertThat(event.getActorId()).isEqualTo(doctor.getId());
        assertThat(event.getActorRole()).isEqualTo("DOCTOR");
        assertThat(event.getRequestId()).isEqualTo(AuditTestSupport.REQUEST_ID);
        assertThat(event.getIp()).isEqualTo(AuditTestSupport.CLIENT_IP);
        assertThat(event.getPatientId()).isEqualTo(patientId);
        String reason = jdbc.queryForObject(
                "select detail ->> 'reason' from audit_event where id = ?", String.class, event.getId());
        assertThat(reason).isEqualTo("NOT_ASSIGNED");
    }

    @Test
    void recordsAnEventWithoutALoggedInPerson() {
        AuditEvent event = auditService.record(AuditAction.LOGIN_FAILED, Map.of("attempt", 3));
        entityManager.flush();

        assertThat(event.getActorId()).isNull();
        assertThat(event.getActorRole()).isNull();
        assertThat(event.getRequestId()).isNull();
        assertThat(event.getIp()).isNull();
    }

    @Test
    void storesNoDetailWhenNoneIsGiven() {
        AuditEvent event = auditService.record(AuditAction.LOGOUT, null);
        entityManager.flush();

        Long withDetail = jdbc.queryForObject("select count(*) from audit_event where detail is not null", Long.class);
        assertThat(event.getDetail()).isNull();
        assertThat(withDetail).isZero();
    }

    @Test
    void rejectsAnOversizedDetail() {
        Map<String, String> tooLong = Map.of("note", "x".repeat(AuditService.MAX_DETAIL_BYTES));

        assertThatThrownBy(() -> auditService.record(AuditAction.LOGOUT, tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids and codes");
    }

    @Test
    void accessLogRecordsTheLoggedInPerson() {
        Staff doctor = doctor();
        AuditTestSupport.logIn(doctor.getId(), "DOCTOR");
        UUID patientId = UUID.randomUUID();

        AccessLogEntry entry = accessLogService.record(patientId, null, AccessKind.NOTE_OPENED);
        entityManager.flush();

        assertThat(entry.getActorId()).isEqualTo(doctor.getId());
        assertThat(entry.getOccurredAt()).isEqualTo(FixedClockConfiguration.NOW);
        assertThat(entry.getWhat()).isEqualTo(AccessKind.NOTE_OPENED);
    }

    @Test
    void accessLogRefusesAnAnonymousOpening() {
        assertThatThrownBy(() -> accessLogService.record(UUID.randomUUID(), null, AccessKind.NOTE_OPENED))
                .isInstanceOf(IllegalStateException.class);
    }
}
