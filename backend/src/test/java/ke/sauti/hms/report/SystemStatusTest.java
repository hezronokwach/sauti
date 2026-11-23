package ke.sauti.hms.report;

import static ke.sauti.hms.support.ActingAs.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import ke.sauti.hms.TestcontainersConfiguration;
import ke.sauti.hms.common.error.RateLimitedException;
import ke.sauti.hms.common.ratelimit.RateLimits;
import ke.sauti.hms.common.ratelimit.RateLimits.Limit;
import ke.sauti.hms.staff.Staff;
import ke.sauti.hms.staff.StaffRepository;
import ke.sauti.hms.staff.StaffRole;
import ke.sauti.hms.support.ActingAs;
import ke.sauti.hms.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A failed job and a failed backup both show, and nothing private does. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SystemStatusTest {

    private static final String STATUS = "/api/admin/status";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTestRows() {
        jdbc.update("delete from backup_run");
        jdbc.update("delete from job where type like 'STATUS_TEST%'");
    }

    private void backup(String result, Duration ago) {
        Instant at = Instant.now().minus(ago);
        boolean good = "SUCCESS".equals(result);
        jdbc.update(
                "insert into backup_run (started_at, finished_at, status, file_name, size_bytes)"
                        + " values (?, ?, ?, ?, ?)",
                java.sql.Timestamp.from(at),
                java.sql.Timestamp.from(at),
                result,
                good ? "sauti-test.dump.age" : null,
                good ? 100L : null);
    }

    @Test
    void aFailedJobIsVisibleWithoutItsErrorText() throws Exception {
        String type = "STATUS_TEST_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "insert into job (type, payload_id, status, attempts, run_after, last_error, updated_at)"
                        + " values (?, ?, 'FAILED', 5, now(), 'Patient Secretname failed', now())",
                type,
                UUID.randomUUID());
        Staff admin = ActingAs.staff(staffRepository, StaffRole.ADMIN);

        String body = mvc.perform(get(STATUS).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs.failedByType[?(@.type == '" + type + "')].count")
                        .value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("Secretname");
    }

    @Test
    void aFailedBackupIsVisibleAndMakesItOverdue() throws Exception {
        backup("SUCCESS", Duration.ofHours(30));
        backup("FAILED", Duration.ofHours(1));
        Staff admin = ActingAs.staff(staffRepository, StaffRole.ADMIN);

        mvc.perform(get(STATUS).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backup.lastResult").value("FAILED"))
                .andExpect(jsonPath("$.backup.lastSuccessAt").exists())
                .andExpect(jsonPath("$.backup.overdue").value(true));
    }

    @Test
    void aRecentGoodBackupIsNotOverdue() throws Exception {
        backup("SUCCESS", Duration.ofHours(2));
        Staff admin = ActingAs.staff(staffRepository, StaffRole.ADMIN);

        mvc.perform(get(STATUS).with(as(admin)))
                .andExpect(jsonPath("$.backup.lastResult").value("SUCCESS"))
                .andExpect(jsonPath("$.backup.overdue").value(false));
    }

    @Test
    void noBackupEverIsOverdue() throws Exception {
        Staff admin = ActingAs.staff(staffRepository, StaffRole.ADMIN);

        mvc.perform(get(STATUS).with(as(admin)))
                .andExpect(jsonPath("$.backup.lastResult").doesNotExist())
                .andExpect(jsonPath("$.backup.overdue").value(true));
    }

    @Test
    void refusedRequestsAreCountedForADay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-24T08:00:00Z"));
        RateLimits limits = new RateLimits(true, clock);
        for (long i = 0; i < Limit.LOGIN_ACCOUNT.capacity(); i++) {
            limits.check(Limit.LOGIN_ACCOUNT, "someone");
        }
        assertThatThrownBy(() -> limits.check(Limit.LOGIN_ACCOUNT, "someone")).isInstanceOf(RateLimitedException.class);

        assertThat(limits.blocksSince(clock.instant().minus(Duration.ofHours(24))))
                .containsEntry(Limit.LOGIN_ACCOUNT, 1L);
        clock.advance(Duration.ofHours(25));
        assertThat(limits.blocksSince(clock.instant().minus(Duration.ofHours(24))))
                .isEmpty();
    }
}
